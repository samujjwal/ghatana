package com.ghatana.refactorer.rewriters;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge for performing Python code transformations using LibCST.
 *
 * <p>This class provides methods to apply code transformations to Python files using the LibCST
 * library. It supports virtual environments, package management, and common Python code patterns.
 
 * @doc.type class
 * @doc.purpose Handles py lib cst bridge operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class PyLibCSTBridge {
    private static final Logger log = LoggerFactory.getLogger(PyLibCSTBridge.class);
    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final String[] PYTHON_EXECUTABLES = {"python3", "python"};

    private final String pythonExecutable;
    private final Path virtualEnvPath;
    private final long timeoutMs;

    /**
 * Creates a new PyLibCSTBridge with default settings. */
    public PyLibCSTBridge() {
        this(null, null, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a new PyLibCSTBridge with custom settings.
     *
     * @param pythonExecutable Path to Python executable (or null to auto-detect)
     * @param virtualEnvPath Path to Python virtual environment (or null if not using one)
     * @param timeoutMs Maximum time to wait for operations to complete
     */
    public PyLibCSTBridge(String pythonExecutable, Path virtualEnvPath, long timeoutMs) {
        this.pythonExecutable = findPythonExecutable(pythonExecutable);
        this.virtualEnvPath = virtualEnvPath;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    /**
     * Gets the path to the Python executable being used by this bridge.
     *
     * @return Path to the Python executable, or null if not set
     */
    public String getPythonExecutable() {
        return pythonExecutable;
    }

    /**
     * Applies a LibCST transform to the specified Python files.
     *
     * @param cwd Working directory for the Python process
     * @param transformPath Path to the transform script
     * @param filePaths Files to transform
     * @return Process result with exit code and output
     */
    public ProcessExec.Result transform(Path cwd, Path transformPath, List<Path> filePaths) {
        return transform(cwd, transformPath, filePaths, Map.of());
    }

    /**
     * Applies a LibCST transform with custom options.
     *
     * @param cwd Working directory for the Python process
     * @param transformPath Path to the transform script
     * @param filePaths Files to transform
     * @param options Additional options to pass to the transform script
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
        command.add(pythonExecutable);
        command.add(transformPath.toAbsolutePath().toString());

        // Add options
        options.forEach(
                (key, value) -> {
                    command.add("--" + key);
                    if (value != null && !value.isEmpty()) {
                        command.add(value);
                    }
                });

        // Add file paths
        filePaths.stream().map(Path::toAbsolutePath).map(Path::toString).forEach(command::add);

        log.info("Running Python transform: {}", String.join(" ", command));
        return ProcessExec.run(cwd, Duration.ofMillis(timeoutMs), command, getEnvironment());
    }

    /**
     * Installs required Python packages using pip.
     *
     * @param cwd Working directory
     * @param packages List of package specs (e.g., "libcst", "black==21.12b0")
     * @return true if installation was successful
     */
    public boolean installPackages(Path cwd, List<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return true;
        }

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("pip");
        command.add("install");
        command.addAll(packages);

        log.info("Installing Python packages: {}", String.join(" ", packages));
        ProcessExec.Result result =
                ProcessExec.run(cwd, Duration.ofMillis(timeoutMs), command, getEnvironment());

        if (result.exitCode() != 0) {
            log.error("Failed to install packages: {}", result.err());
            return false;
        }
        return true;
    }

    /**
     * Creates a new Python virtual environment.
     *
     * @param path Path to create the virtual environment in
     * @return true if creation was successful
     */
    public boolean createVirtualEnv(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Failed to create virtualenv directory: {}", e.getMessage());
            return false;
        }

        List<String> command =
                List.of(pythonExecutable, "-m", "venv", path.toAbsolutePath().toString());

        log.info("Creating Python virtual environment at {}", path);
        ProcessExec.Result result =
                ProcessExec.run(
                        path.getParent(),
                        Duration.ofMillis(timeoutMs * 2),
                        command,
                        getEnvironment());

        return result.exitCode() == 0;
    }

    /**
     * Gets the environment variables for Python processes. This includes virtual environment
     * activation if configured.
     */
    private Map<String, String> getEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());

        if (virtualEnvPath != null) {
            String pathSep = System.getProperty("path.separator");
            String path = virtualEnvPath.resolve("bin").toAbsolutePath().toString();

            // Update PATH to include virtualenv bin directory
            env.merge("PATH", path, (oldPath, newPath) -> newPath + pathSep + oldPath);

            // Set VIRTUAL_ENV for Python tools
            env.put("VIRTUAL_ENV", virtualEnvPath.toAbsolutePath().toString());
        }

        return env;
    }

    /**
     * Finds the Python executable to use.
     *
     * @param preferred Path to preferred Python executable, or null to auto-detect
     * @return Path to Python executable
     * @throws IllegalStateException if no suitable Python executable is found
     */
    private static String findPythonExecutable(String preferred) {
        // Use preferred if specified and exists
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred;
        }

        // Check common Python executable names
        for (String exec : PYTHON_EXECUTABLES) {
            try {
                Process process =
                        new ProcessBuilder(exec, "--version").redirectErrorStream(true).start();

                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return exec;
                }
            } catch (Exception e) {
                // Try next executable
            }
        }

        throw new IllegalStateException(
                "No suitable Python 3 executable found. Please install Python 3 and ensure it's in"
                        + " your PATH, or specify the path to the Python executable.");
    }

    /**
     * Checks if LibCST is installed in the current Python environment.
     *
     * @return true if LibCST is available, false otherwise
     */
    public boolean isLibCSTAvailable() {
        try {
            Process process =
                    new ProcessBuilder(pythonExecutable, "-c", "import libcst; print('ok')")
                            .redirectErrorStream(true)
                            .start();

            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
