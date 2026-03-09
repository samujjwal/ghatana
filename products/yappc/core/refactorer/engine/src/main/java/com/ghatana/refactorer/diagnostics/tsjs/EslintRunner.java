package com.ghatana.refactorer.diagnostics.tsjs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runs ESLint on TypeScript/JavaScript files and parses the output. Supports
 * both reporting issues
 * and automatically fixing them.
 * 
 * @doc.type runner
 * @doc.language typescript,javascript
 * @doc.tool eslint
 
 * @doc.purpose Handles eslint runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class EslintRunner {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final String BRIDGE_SCRIPT = "bridges/eslint/run-eslint.mjs";
    private static final Logger logger = LogManager.getLogger(EslintRunner.class);

    private final PolyfixProjectContext context;
    private final ProcessRunner processRunner;
    private boolean fixEnabled = false;
    private List<String> includePatterns = List.of("**/*.{js,jsx,ts,tsx}");
    private List<String> ignorePatterns = List.of("**/node_modules/**", "**/dist/**");
    private int maxWarnings = -1; // -1 means no limit

    public EslintRunner(PolyfixProjectContext context) {
        this.context = context;
        this.processRunner = new ProcessRunner(context);
    }

    /**
     * Runs ESLint on the specified project root.
     *
     * @param projectRoot The root directory of the project
     * @return A future that completes with the list of diagnostics
     */
    public Promise<List<UnifiedDiagnostic>> run(Path projectRoot) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        Path bridgeScript = getBridgeScriptPath();
                        if (bridgeScript == null) {
                            logger.warn("ESLint bridge script not found. Skipping ESLint.");
                            return List.<UnifiedDiagnostic>of();
                        }

                        // Check for local ESLint installation
                        Path localEslintBin = projectRoot.resolve("node_modules/.bin/eslint");
                        boolean useLocalEslint = Files.exists(localEslintBin);

                        if (useLocalEslint) {
                            logger.debug("Using local ESLint installation at: {}", localEslintBin);
                        } else {
                            logger.debug("Local ESLint not found at: {}", localEslintBin);
                        }

                        // Build command arguments
                        List<String> args = new ArrayList<>();

                        // If using local ESLint, add the local node_modules/.bin to PATH
                        Map<String, String> env = new HashMap<>();
                        if (useLocalEslint) {
                            String path = System.getenv("PATH");
                            String nodeModulesBin = projectRoot.resolve("node_modules/.bin").toString();
                            if (path != null) {
                                env.put("PATH", nodeModulesBin + File.pathSeparator + path);
                            } else {
                                env.put("PATH", nodeModulesBin);
                            }
                        }

                        // Add bridge script and project root
                        args.add(bridgeScript.toString());
                        args.add(projectRoot.toAbsolutePath().toString());

                        // Add flags
                        if (fixEnabled) {
                            args.add("--fix");
                            logger.info("ESLint auto-fix is enabled");
                        }

                        // If includePatterns is empty, use the project root
                        if (includePatterns.isEmpty()) {
                            includePatterns = List.of("**/*.{js,jsx,ts,tsx}");
                        }

                        // Add include patterns
                        for (String pattern : includePatterns) {
                            args.add("--include");
                            args.add(pattern);
                        }

                        // Add ignore patterns
                        for (String pattern : ignorePatterns) {
                            args.add("--ignore");
                            args.add(pattern);
                        }

                        // Add max warnings if specified
                        if (maxWarnings >= 0) {
                            args.add("--max-warnings");
                            args.add(String.valueOf(maxWarnings));
                        }

                        logger.debug("Running ESLint with arguments: {}", args);

                        // Execute with environment variables if any
                        ProcessResult result = processRunner.execute(
                                env.isEmpty() ? null : env,
                                "node",
                                args,
                                projectRoot.toAbsolutePath(),
                                true);

                        // Log the command output for debugging
                        if (logger.isDebugEnabled()) {
                            logger.debug("ESLint stdout: {}", result.output());
                        }
                        if (result.error() != null && !result.error().isEmpty()) {
                            logger.debug("ESLint stderr: {}", result.error());
                        }

                        if (!result.isSuccess()) {
                            handleError(result);
                            if (logger.isInfoEnabled()
                                    && result.error() != null
                                    && !result.error().isEmpty()) {
                                logger.info(
                                        "ESLint stderr (first 20 lines): {}",
                                        result.error()
                                                .lines()
                                                .limit(20)
                                                .collect(Collectors.joining("\n")));
                            }
                            // Even if there were errors, we might still have some results
                            if (result.output() != null && !result.output().trim().isEmpty()) {
                                return parseEslintOutput(result.output());
                            }
                            return List.<UnifiedDiagnostic>of();
                        }

                        // Parse ESLint output
                        return parseEslintOutput(result.output());

                    } catch (Exception e) {
                        logger.error("Error running ESLint: {}", e.getMessage(), e);
                        return List.<UnifiedDiagnostic>of();
                    }
                });
    }

    private void handleError(ProcessResult result) {
        if (result.exitCode() == 2) {
            logger.warn(
                    "ESLint found issues that cannot be auto-fixed: {}",
                    result.error().lines().limit(10).collect(Collectors.joining("\n")));
        } else if (result.exitCode() == 1) {
            logger.error(
                    "ESLint found errors: {}",
                    result.error().lines().limit(10).collect(Collectors.joining("\n")));
        } else {
            logger.error(
                    "ESLint failed with exit code {}: {}",
                    result.exitCode(),
                    result.error().lines().limit(10).collect(Collectors.joining("\n")));
        }
    }

    /**
     * Enables or disables automatic fixing of issues.
     *
     * @param enabled Whether to enable auto-fixing
     * @return This instance for method chaining
     */
    public EslintRunner withFix(boolean enabled) {
        this.fixEnabled = enabled;
        return this;
    }

    /**
     * Sets the file patterns to include in the linting process.
     *
     * @param patterns List of glob patterns
     * @return This instance for method chaining
     */
    public EslintRunner withIncludePatterns(List<String> patterns) {
        this.includePatterns = new ArrayList<>(patterns);
        return this;
    }

    /**
     * Sets the file patterns to ignore during linting.
     *
     * @param patterns List of glob patterns to ignore
     * @return This instance for method chaining
     */
    public EslintRunner withIgnorePatterns(List<String> patterns) {
        this.ignorePatterns = new ArrayList<>(patterns);
        return this;
    }

    /**
     * Sets the maximum number of warnings before considering the run as failed.
     *
     * @param maxWarnings Maximum number of warnings, or -1 for no limit
     * @return This instance for method chaining
     */
    public EslintRunner withMaxWarnings(int maxWarnings) {
        this.maxWarnings = maxWarnings;
        return this;
    }

    /**
     * Checks if ESLint is available in the project.
     *
     * @return true if ESLint is available, false otherwise
     */
    public boolean isAvailable() {
        try {
            Path projectRoot = context.workingDir();
            // Check for ESLint configuration files
            boolean hasEslintConfig = StreamSupport.stream(
                    Files.find(
                            projectRoot,
                            3,
                            (path, attrs) -> path.getFileName()
                                    .toString()
                                    .startsWith(".eslintrc")
                                    || path.getFileName()
                                            .toString()
                                            .equals(
                                                    "eslint.config.js")
                                    || path.getFileName()
                                            .toString()
                                            .equals(
                                                    "eslint.config.mjs"))
                            .spliterator(),
                    false)
                    .findAny()
                    .isPresent();

            // Check for ESLint in package.json
            Path packageJson = projectRoot.resolve("package.json");
            if (Files.exists(packageJson)) {
                try {
                    ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
                    JsonNode pkg = objectMapper.readTree(packageJson.toFile());
                    if (pkg.has("devDependencies") && pkg.get("devDependencies").has("eslint")) {
                        return true;
                    }
                    if (pkg.has("dependencies") && pkg.get("dependencies").has("eslint")) {
                        return true;
                    }
                } catch (IOException e) {
                    logger.debug("Error reading package.json: {}", e.getMessage());
                }
            }

            return hasEslintConfig;
        } catch (Exception e) {
            logger.debug("Error checking ESLint availability: {}", e.getMessage());
            return false;
        }
    }

    private Path getBridgeScriptPath() {
        try {
            // Try to get from resources
            java.net.URL resourceUrl = getClass().getClassLoader().getResource(BRIDGE_SCRIPT);
            if (resourceUrl != null) {
                try {
                    return Paths.get(resourceUrl.toURI());
                } catch (Exception e) {
                    logger.warn("Failed to convert resource URL to URI: {}", e.getMessage());
                }
            }

            // Try to find in common locations
            Path[] possiblePaths = {
                    Paths.get("modules/diagnostics/src/main/resources", BRIDGE_SCRIPT),
                    Paths.get("src/main/resources", BRIDGE_SCRIPT)
            };

            for (Path path : possiblePaths) {
                if (Files.exists(path)) {
                    return path.toAbsolutePath();
                }
            }

            logger.error("ESLint bridge script not found in any location");
            return null;

        } catch (Exception e) {
            logger.error("Failed to locate ESLint bridge script: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<UnifiedDiagnostic> parseEslintOutput(String jsonOutput) {
        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        if (jsonOutput == null || jsonOutput.trim().isEmpty()) {
            return diagnostics;
        }

        try {
            ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
            JsonNode root = objectMapper.readTree(jsonOutput);

            if (!root.isArray()) {
                logger.warn("Expected array in ESLint output, got: {}", root.getNodeType());
                return diagnostics;
            }

            for (JsonNode fileNode : root) {
                if (!fileNode.has("filePath") || !fileNode.has("messages")) {
                    continue;
                }

                String filePath = fileNode.get("filePath").asText();
                JsonNode messages = fileNode.get("messages");

                for (JsonNode msg : messages) {
                    UnifiedDiagnostic diagnostic = createDiagnostic(filePath, msg);
                    if (diagnostic != null) {
                        diagnostics.add(diagnostic);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing ESLint output: {}", e.getMessage(), e);
        }

        return diagnostics;
    }

    private UnifiedDiagnostic createDiagnostic(String filePath, JsonNode msg) {
        try {
            String ruleId = msg.has("ruleId") ? msg.get("ruleId").asText() : "unknown";
            int severity = msg.get("severity").asInt(); // 1=warning, 2=error
            String message = msg.get("message").asText();
            int line = msg.get("line").asInt(-1);
            int column = msg.get("column").asInt(-1);

            // Create metadata map for additional ESLint message properties
            Map<String, String> metadata = new HashMap<>();
            if (msg.has("endLine"))
                metadata.put("endLine", String.valueOf(msg.get("endLine").asInt()));
            if (msg.has("endColumn"))
                metadata.put("endColumn", String.valueOf(msg.get("endColumn").asInt()));
            if (msg.has("nodeType"))
                metadata.put("nodeType", msg.get("nodeType").asText());

            // Create the appropriate diagnostic based on severity
            if (severity == 2) {
                return UnifiedDiagnostic.error(
                        "eslint",
                        ruleId + ": " + message,
                        filePath != null ? Path.of(filePath) : null,
                        line,
                        column,
                        null);
            } else {
                return UnifiedDiagnostic.warning(
                        "eslint",
                        ruleId + ": " + message,
                        filePath != null ? Path.of(filePath) : null,
                        line,
                        column,
                        null);
            }
        } catch (Exception e) {
            logger.warn("Error creating diagnostic from ESLint message: {}", msg, e);
            return null;
        }
    }
}
