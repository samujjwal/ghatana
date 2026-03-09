package com.ghatana.refactorer.rewriters;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bridge to jscodeshift for performing code transformations on JavaScript and TypeScript code.
 *
 * <p>This class provides a Java API to run jscodeshift transforms with proper error handling and
 * TypeScript support.
 
 * @doc.type class
 * @doc.purpose Handles js code shift bridge operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class JsCodeShiftBridge {
    private static final Logger log = LoggerFactory.getLogger(JsCodeShiftBridge.class);
    private static final long DEFAULT_TIMEOUT_MS = 30_000; // 30 seconds
    private static final String DEFAULT_PACKAGE_MANAGER = "npm";

    private final String jscodeshiftPath;
    private final List<String> customJscodeshiftCommand;
    private final boolean useCustomCommand;
    private final long timeoutMs;
    private final String packageManager;
    private final boolean useTypeScript;
    private final boolean installDeps;
    private final int verboseLevel;

    /**
 * Builder for JsCodeShiftBridge configuration. */
    public static class Builder {
        private String jscodeshiftPath = "npx";
        private List<String> jscodeshiftCommand = List.of();
        private boolean useCustomCommand = false;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;
        private String packageManager = DEFAULT_PACKAGE_MANAGER;
        private boolean useTypeScript = true;
        private boolean installDeps = true;
        private int verboseLevel = 1; // default moderate verbosity

        public Builder withJscodeshiftPath(String path) {
            this.jscodeshiftPath = Objects.requireNonNull(path, "path");
            this.jscodeshiftCommand = List.of();
            this.useCustomCommand = false;
            return this;
        }

        public Builder withJscodeshiftCommand(List<String> command) {
            Objects.requireNonNull(command, "command");
            if (command.isEmpty()) {
                throw new IllegalArgumentException("jscodeshift command cannot be empty");
            }
            this.jscodeshiftCommand = List.copyOf(command);
            this.useCustomCommand = true;
            return this;
        }

        public Builder withTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder withPackageManager(String packageManager) {
            this.packageManager = packageManager;
            return this;
        }

        public Builder withTypeScript(boolean useTypeScript) {
            this.useTypeScript = useTypeScript;
            return this;
        }

        public Builder withDependencyInstallation(boolean installDeps) {
            this.installDeps = installDeps;
            return this;
        }

        /**
 * Set jscodeshift verbosity level (0 to disable). */
        public Builder withVerboseLevel(int verboseLevel) {
            this.verboseLevel = Math.max(0, verboseLevel);
            return this;
        }

        public JsCodeShiftBridge build() {
            return new JsCodeShiftBridge(this);
        }
    }

    /**
 * Creates a new JsCodeShiftBridge with default settings. */
    public static JsCodeShiftBridge create() {
        return new Builder().build();
    }

    /**
     * Creates a new builder for configuring and creating a JsCodeShiftBridge instance.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new JsCodeShiftBridge with custom settings.
     *
     * @param jscodeshiftPath Path to jscodeshift executable or 'npx' to use npx
     * @param timeoutMs Maximum time to wait for jscodeshift to complete
     */
    public JsCodeShiftBridge(String jscodeshiftPath, long timeoutMs) {
        this(new Builder().withJscodeshiftPath(jscodeshiftPath).withTimeoutMs(timeoutMs));
    }

    /**
     * Creates a transform that renames a function or variable from oldName to newName.
     *
     * @param oldName The name to be replaced
     * @param newName The new name to use
     * @return A jscodeshift transform as a string
     */
    public static String createRenameTransform(String oldName, String newName) {
        return String.format(
                "/**\n"
                    + "             * Renames '%s' to '%s' in the code.\n"
                    + "             */\n"
                    + "            module.exports = function(file, api) {\n"
                    + "              const j = api.jscodeshift;\n"
                    + "              const root = j(file.source);\n"
                    + "              \n"
                    + "              // Find all identifiers with the old name\n"
                    + "              root\n"
                    + "                .find(j.Identifier, { name: '%s' })\n"
                    + "                .filter(path => {\n"
                    + "                  // Skip if this is part of a member expression (e.g.,"
                    + " obj.oldName)\n"
                    + "                  return !j.MemberExpression.check(path.parent.node) || \n"
                    + "                         path.parent.node.property !== path.node;\n"
                    + "                })\n"
                    + "                .forEach(path => {\n"
                    + "                  j(path).replaceWith(\n"
                    + "                    j.identifier('%s')\n"
                    + "                  );\n"
                    + "                });\n"
                    + "              \n"
                    + "              return root.toSource();\n"
                    + "            };",
                oldName, newName, oldName, newName);
    }

    private JsCodeShiftBridge(Builder builder) {
        this.jscodeshiftPath = Objects.requireNonNull(builder.jscodeshiftPath, "jscodeshiftPath");
        this.customJscodeshiftCommand =
                builder.useCustomCommand ? List.copyOf(builder.jscodeshiftCommand) : List.of();
        this.useCustomCommand = builder.useCustomCommand;
        this.timeoutMs = builder.timeoutMs > 0 ? builder.timeoutMs : DEFAULT_TIMEOUT_MS;
        this.packageManager = builder.packageManager;
        this.useTypeScript = builder.useTypeScript;
        this.installDeps = builder.installDeps;
        this.verboseLevel = builder.verboseLevel;
    }

    /**
     * Applies a jscodeshift transform to the specified files.
     *
     * @param cwd Working directory (repo root)
     * @param transformPath Path to the transform file
     * @param filePaths Files or directories to transform
     * @return Process result with exit code and output
     */
    public ProcessExec.Result transform(Path cwd, Path transformPath, List<Path> filePaths) {
        return transform(cwd, transformPath, filePaths, Map.of());
    }

    /**
     * Applies a jscodeshift transform to the specified files with custom options.
     *
     * @param cwd Working directory (repo root)
     * @param transformPath Path to the transform file
     * @param filePaths Files or directories to transform
     * @param options Additional options to pass to jscodeshift
     * @return Process result with exit code and output
     */
    public ProcessExec.Result transform(
            Path cwd, Path transformPath, List<Path> filePaths, Map<String, String> options) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(transformPath, "transformPath");
        Objects.requireNonNull(filePaths, "filePaths");
        Objects.requireNonNull(options, "options");

        if (filePaths.isEmpty()) {
            throw new IllegalArgumentException("No files to transform");
        }

        // Build the command
        List<String> command = new ArrayList<>();

        if (useCustomCommand) {
            command.addAll(customJscodeshiftCommand);
        } else {
            // Prefer local jscodeshift if available: <cwd>/node_modules/.bin/jscodeshift
            Path localJsCodeShift =
                    cwd.resolve("node_modules").resolve(".bin").resolve("jscodeshift");
            boolean useLocal =
                    java.nio.file.Files.isRegularFile(localJsCodeShift)
                            && localJsCodeShift.toFile().canExecute();

            if (useLocal) {
                command.add(localJsCodeShift.toAbsolutePath().toString());
            } else if (!"npx".equals(jscodeshiftPath)) {
                command.add(jscodeshiftPath);
            } else {
                // Fallback to npx
                command.add("npx");
                if (installDeps) {
                    command.add("--yes"); // Allow automatic installation if permitted
                    command.add("jscodeshift@latest");
                } else {
                    command.add("--no-install");
                    command.add("jscodeshift");
                }
            }
        }

        // Add common options
        if (verboseLevel > 0) {
            command.add("--verbose=" + verboseLevel);
        }
        command.addAll(
                List.of(
                        "--run-in-band",
                        "--fail-on-error",
                        "--extensions=js,jsx,ts,tsx",
                        useTypeScript ? "--parser=tsx" : "--parser=babel",
                        "--ignore-pattern=**/node_modules/**"));

        // Add transform file
        command.add("--transform");
        command.add(transformPath.toAbsolutePath().toString());

        // Add options
        options.forEach(
                (key, value) -> {
                    command.add("--" + key);
                    if (value != null && !value.isEmpty()) {
                        command.add(value);
                    }
                });

        // Add file paths (use absolute paths to avoid edge-cases with cwd relativity)
        List<String> filePathStrings =
                filePaths.stream()
                        .map(path -> path.toAbsolutePath().toString())
                        .collect(Collectors.toList());
        command.addAll(filePathStrings);

        // Log the full command for debugging
        logCommand(command);
        log.info("Working directory: {}", cwd.toAbsolutePath());
        log.info("File paths to transform (relative to cwd): {}", filePathStrings);

        try {
            // Log environment info
            log.info("Environment PATH: {}", System.getenv("PATH"));
            log.info("Current directory: {}", Path.of(".").toAbsolutePath());

            // Execute the command with environment variables
            Map<String, String> env = new HashMap<>();
            env.put("DEBUG", "jscodeshift*"); // Enable debug logging in jscodeshift

            ProcessExec.Result result =
                    ProcessExec.run(
                            cwd,
                            Duration.ofMillis(timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS),
                            command,
                            env);

            // Log command output
            log.info("Command stdout:\n{}", result.out());
            log.info("Command stderr:\n{}", result.err());

            // If command failed, log more details
            if (result.exitCode() != 0) {
                log.error("jscodeshift failed with exit code: {}", result.exitCode());
                log.error("Command: {}", String.join(" ", command));
                log.error("Working directory: {}", cwd.toAbsolutePath());
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to execute jscodeshift command", e);
            throw new RuntimeException(
                    "Failed to execute jscodeshift command: " + e.getMessage(), e);
        }
    }

    private void logCommand(List<String> command) {
        log.info("Running command: {}", String.join(" ", command));
    }

    /**
     * Runs a custom jscodeshift command.
     *
     * @param cwd Working directory (repo root)
     * @param command Full command to execute (including jscodeshift)
     * @param timeoutMs Maximum time to wait for the command to complete
     * @return Process result with exit code and output
     */
    public ProcessExec.Result run(Path cwd, List<String> command, long timeoutMs) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(command, "command");

        try {
            return ProcessExec.run(
                    cwd,
                    Duration.ofMillis(timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS),
                    command,
                    Map.of());
        } catch (Exception e) {
            log.error("Failed to execute jscodeshift command", e);
            throw new RuntimeException("Failed to execute jscodeshift command", e);
        }
    }

    /**
     * Checks if jscodeshift is available.
     *
     * @return true if jscodeshift is available, false otherwise
     */
    public static boolean isAvailable() {
        try {
            // Try without installing first
            Process process =
                    new ProcessBuilder("npx", "--no-install", "jscodeshift", "--version")
                            .redirectErrorStream(true)
                            .start();

            // Wait for the process to complete with a timeout
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            if (process.exitValue() == 0) {
                return true;
            }

            // Fallback to allowing install if necessary
            process =
                    new ProcessBuilder("npx", "--yes", "jscodeshift@latest", "--version")
                            .redirectErrorStream(true)
                            .start();
            finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
