package com.ghatana.refactorer.diagnostics.tsjs;

import com.ghatana.refactorer.diagnostics.tsjs.model.TscDiagnostic;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.diagnostics.DiagnosticRunner;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import com.ghatana.refactorer.shared.util.JsonSupport;
import io.activej.promise.Promise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Logger;

/**
 * Runs TypeScript compiler (TSC) to collect type checking diagnostics.
 * 
 * @doc.type runner
 * @doc.language typescript
 * @doc.tool tsc
 
 * @doc.purpose Handles tsc runner operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class TscRunner implements DiagnosticRunner {
    private static final String ID = "tsc";
    private static final String BRIDGE_SCRIPT = "bridges/tsc/tsc-diag.mjs";
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private final PolyfixProjectContext context;
    private final Logger logger;
    private final ProcessRunner processRunner;

    public TscRunner(PolyfixProjectContext context) {
        this.context = context;
        this.logger = context.log();
        this.processRunner = new ProcessRunner(context);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> run(Path projectRoot) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR,
                () -> {
                    try {
                        logger.info("Running TypeScript compiler in: {}", projectRoot);

                        // Check if TypeScript is installed
                        if (!isAvailable()) {
                            logger.warn(
                                    "TypeScript is not available in the project. Skipping"
                                            + " TypeScript diagnostics.");
                            return List.of();
                        }

                        // Get the path to the bridge script
                        Path bridgePath = getBridgeScriptPath();
                        if (bridgePath == null) {
                            logger.error("Failed to locate TSC bridge script");
                            return List.of();
                        }

                        // Execute the bridge script
                        ProcessResult result = processRunner.execute(
                                "node", List.of(bridgePath.toString()), projectRoot, true);

                        if (result.exitCode() != 0) {
                            logger.error(
                                    "TSC bridge script failed with exit code {}: {}",
                                    result.exitCode(),
                                    result.error());
                            return List.of();
                        }

                        // Parse the output
                        String output = result.output();
                        if (output == null || output.trim().isEmpty()) {
                            logger.info("No TypeScript diagnostics found");
                            return List.of();
                        }

                        // Parse the JSON output
                        List<TscDiagnostic> tscDiagnostics = JsonSupport.fromJsonToList(result.output(),
                                TscDiagnostic.class);
                        return convertDiagnostics(tscDiagnostics);

                    } catch (Exception e) {
                        logger.error("Error running TSC", e);
                        return List.of();
                    }
                });
    }

    /**
     * Locates the TSC bridge script in various locations with fallbacks. Tries
     * multiple strategies
     * to find the script in different environments.
     */
    private Path getBridgeScriptPath() {
        try {
            // Strategy 1: Try to get the resource from classpath (works in JAR)
            java.net.URL resourceUrl = getClass().getClassLoader().getResource(BRIDGE_SCRIPT);
            if (resourceUrl != null) {
                try {
                    Path path = Paths.get(resourceUrl.toURI());
                    logger.debug("Found TSC bridge script in classpath: {}", path);
                    return path;
                } catch (java.net.URISyntaxException e) {
                    logger.warn("Failed to convert resource URL to URI: {}", e.getMessage());
                }
            }

            // Strategy 2: Try to extract the resource to a temporary file
            try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(BRIDGE_SCRIPT)) {
                if (in != null) {
                    // Create a temporary file with the correct extension
                    String fileName = BRIDGE_SCRIPT.substring(BRIDGE_SCRIPT.lastIndexOf('/') + 1);
                    Path tempFile = Files.createTempFile("tsc-bridge-", "-" + fileName);
                    tempFile.toFile().deleteOnExit();

                    // Copy the resource content
                    Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Extracted TSC bridge script to temporary file: {}", tempFile);

                    // Make the file executable (Unix-like systems)
                    if (!tempFile.toFile().setExecutable(true)) {
                        logger.warn("Failed to set executable permissions on: {}", tempFile);
                    }

                    return tempFile.toAbsolutePath();
                }
            }

            // Strategy 3: Try to find the file in the source tree (for development)
            Path[] possiblePaths = {
                    // Standard Maven/Gradle structure
                    Paths.get("modules/diagnostics/src/main/resources", BRIDGE_SCRIPT),
                    // When running from IDE with different working directory
                    Paths.get("src/main/resources", BRIDGE_SCRIPT),
                    // When running from project root
                    Paths.get("polyfix/modules/diagnostics/src/main/resources", BRIDGE_SCRIPT),
                    // When running from module directory
                    Paths.get("src/main/resources", BRIDGE_SCRIPT)
            };

            for (Path path : possiblePaths) {
                if (Files.exists(path)) {
                    logger.debug("Found TSC bridge script at: {}", path.toAbsolutePath());
                    return path.toAbsolutePath();
                }
            }

            // Strategy 4: Try to find the file relative to the current working directory
            Path currentDir = Paths.get("");
            Path relativePath = currentDir.resolve(BRIDGE_SCRIPT);
            if (Files.exists(relativePath)) {
                logger.debug(
                        "Found TSC bridge script in working directory: {}",
                        relativePath.toAbsolutePath());
                return relativePath.toAbsolutePath();
            }

            // If we get here, we couldn't find the script anywhere
            logger.error(
                    "TSC bridge script '{}' not found in any of the following locations:",
                    BRIDGE_SCRIPT);
            for (Path path : possiblePaths) {
                logger.error(" - {}", path.toAbsolutePath());
            }
            logger.error(" - {}", relativePath.toAbsolutePath());

            return null;

        } catch (Exception e) {
            logger.error("Failed to locate TSC bridge script: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<UnifiedDiagnostic> convertDiagnostics(List<TscDiagnostic> tscDiagnostics) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        for (TscDiagnostic tscDiag : tscDiagnostics) {
            // Map TSC severity to our severity enum
            Severity severity = "error".equalsIgnoreCase(tscDiag.severity) ? Severity.ERROR : Severity.WARNING;

            // Create metadata map with the TSC error code
            Map<String, String> metadata = new HashMap<>();
            if (tscDiag.code != null && !tscDiag.code.isEmpty()) {
                metadata.put("tscCode", tscDiag.code);
            }

            // Create the diagnostic using the appropriate factory method
            if (severity == Severity.ERROR) {
                diagnostics.add(
                        UnifiedDiagnostic.error(
                                "tsc",
                                tscDiag.message,
                                tscDiag.file != null ? Path.of(tscDiag.file) : null,
                                tscDiag.line,
                                tscDiag.column,
                                null));
            } else {
                diagnostics.add(
                        UnifiedDiagnostic.warning(
                                "tsc",
                                tscDiag.message,
                                tscDiag.file != null ? Path.of(tscDiag.file) : null,
                                tscDiag.line,
                                tscDiag.column,
                                null));
            }
        }

        return diagnostics;
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if TypeScript is installed in the project
            Path tscPath = context.workingDir().resolve("node_modules/.bin/tsc");
            boolean tscExists = tscPath.toFile().exists();

            // Also check for node_modules/typescript
            Path typescriptDir = context.workingDir().resolve("node_modules/typescript");
            boolean typescriptDirExists = typescriptDir.toFile().exists();

            if (!tscExists || !typescriptDirExists) {
                logger.debug(
                        "TypeScript not found in project. TSC exists: {}, TypeScript dir exists:"
                                + " {}",
                        tscExists,
                        typescriptDirExists);
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error checking TypeScript availability: {}", e.getMessage(), e);
            return false;
        }
    }

    // TscDiagnostic is now in the model package
}
