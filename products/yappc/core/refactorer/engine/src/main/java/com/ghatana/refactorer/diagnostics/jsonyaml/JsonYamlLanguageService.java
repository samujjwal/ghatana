package com.ghatana.refactorer.diagnostics.jsonyaml;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.reactor.Reactor;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ Promise-based language service for JSON and YAML file validation and diagnostics.
 *
 * <p>This service provides asynchronous validation of JSON and YAML files against JSON Schema
 * using AJV (Another JSON Schema Validator) via Node.js bridge.
 *
 * @doc.type service
 * @doc.language json,yaml
 * @doc.tool ajv
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.purpose Handles json yaml language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public final class JsonYamlLanguageService implements LanguageService {
    private static final Logger log = LoggerFactory.getLogger(JsonYamlLanguageService.class);
    private static final String AJV_VALIDATE_SCRIPT = "/bridges/ajv/ajv-validate.js";
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private volatile Reactor reactor;

    /**
     * Creates a new JsonYamlLanguageService with the current thread's Reactor.
     */
    public JsonYamlLanguageService() {
        this.reactor = null; // Lazy init for ServiceLoader compatibility
    }

    /**
     * Creates a new JsonYamlLanguageService with the specified Reactor.
     *
     * @param reactor the Reactor to use for async operations
     */
    public JsonYamlLanguageService(Reactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public String id() {
        return "jsonyaml";
    }

    @Override
    public boolean supports(Path file) {
        if (file == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext context, List<Path> files) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(files, "files");

        List<Path> validFiles = files.stream()
                .filter(Objects::nonNull)
                .filter(this::supports)
                .filter(Files::exists)
                .collect(Collectors.toList());

        if (validFiles.isEmpty()) {
            return Promise.of(List.of());
        }

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> new NodeBridge(context))
                .then(nodeBridge -> Promises.toList(validFiles.stream()
                                .map(file -> validateFile(context, nodeBridge, file))
                                .collect(Collectors.toList()))
                        .map(listOfLists -> listOfLists.stream()
                                .flatMap(List::stream)
                                .collect(Collectors.toList())));
    }

    /**
     * Validates a single JSON/YAML file asynchronously.
     *
     * @param context the project context
     * @param nodeBridge the Node.js bridge for AJV execution
     * @param file the file to validate
     * @return a Promise resolving to a list of diagnostics for this file
     */
    private Promise<List<UnifiedDiagnostic>> validateFile(
            PolyfixProjectContext context, NodeBridge nodeBridge, Path file) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();
            
            try {
                Path schemaFile = findSchemaForFile(file, context.root());
                if (schemaFile == null) {
                    log.debug("No schema found for file: {}", file);
                    return diagnostics;
                }

                log.debug("Validating {} against schema {}", file, schemaFile);

                // Execute the AJV validation script via NodeBridge
                NodeBridge.Result result = nodeBridge.executeScript(
                        AJV_VALIDATE_SCRIPT,
                        schemaFile.toAbsolutePath().toString(),
                        file.toAbsolutePath().toString());

                if (result.exitCode() != 0) {
                    // Parse the error output
                    String errorOutput = result.stderr().isEmpty() ? result.stdout() : result.stderr();
                    String errorMessage = errorOutput.contains("{\n")
                            ? errorOutput.substring(0, errorOutput.indexOf("{\n"))
                            : errorOutput;

                    diagnostics.add(
                            createErrorDiagnostic(
                                    file, "Schema validation failed: " + errorMessage.trim()));
                } else {
                    // Parse the JSON output for detailed validation errors
                    String jsonOutput = result.stdout();
                    if (jsonOutput != null && jsonOutput.startsWith("{")) {
                        // In a real implementation, we would parse the JSON and extract detailed errors
                        // For now, we'll just log the raw output
                        log.debug("Validation output for {}: {}", file, jsonOutput);

                        // NOTE: Parse JSON output and create detailed diagnostics
                        // This would involve creating a proper JSON parser and mapping AJV errors to
                        // diagnostics
                    }
                }
            } catch (Exception e) {
                log.error("Error validating file: " + file, e);
                diagnostics.add(
                        createErrorDiagnostic(file, "Validation error: " + e.getMessage()));
            }
            
            return diagnostics;
        });
    }

    private Path findSchemaForFile(Path file, Path projectRoot) {
        // Simple schema discovery: look for a .schema.json file with the same base name
        String baseName = file.getFileName().toString();
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        // Check in the same directory first
        Path schemaFile = file.getParent().resolve(baseName + ".schema.json");
        if (Files.exists(schemaFile)) {
            return schemaFile;
        }

        // Then check in the project's schema directory
        schemaFile = projectRoot.resolve("schemas").resolve(baseName + ".schema.json");
        if (Files.exists(schemaFile)) {
            return schemaFile;
        }

        // Finally, check for a generic schema
        schemaFile = projectRoot.resolve("schemas").resolve("schema.json");
        return Files.exists(schemaFile) ? schemaFile : null;
    }

    private UnifiedDiagnostic createErrorDiagnostic(Path file, String message) {
        return UnifiedDiagnostic.error(
                "jsonyaml",
                message,
                file.toString(),
                -1, // Line number (not available from AJV)
                -1, // Column number (not available from AJV)
                null // No exception
        );
    }

    @Override
    public List<String> getSupportedFileExtensions() {
        return List.of(".json", ".yaml", ".yml");
    }
}
