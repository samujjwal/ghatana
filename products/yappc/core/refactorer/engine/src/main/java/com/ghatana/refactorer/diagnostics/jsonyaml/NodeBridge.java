package com.ghatana.refactorer.diagnostics.jsonyaml;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.util.ProcessExec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thin wrapper around the embedded Node.js bridge scripts used for JSON/YAML validation.
 *
 * <p>The bridge copies a bundled Node.js script to a temporary location and executes it using the
 * configured Node binary (falls back to {@code node} on the PATH). Callers provide both the schema
 * and target file paths that are forwarded to the script.
 
 * @doc.type class
 * @doc.purpose Handles node bridge operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class NodeBridge {
    private static final Logger log = LogManager.getLogger(NodeBridge.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final PolyfixProjectContext context;
    private final Duration timeout;

    public NodeBridge(PolyfixProjectContext context) {
        this(context, DEFAULT_TIMEOUT);
    }

    NodeBridge(PolyfixProjectContext context, Duration timeout) {
        this.context = Objects.requireNonNull(context, "context");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Result value object returned from Node bridge executions. */
    public static final class Result {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int exitCode() {
            return exitCode;
        }

        public String stdout() {
            return stdout;
        }

        public String stderr() {
            return stderr;
        }
    }

    /**
     * Execute an embedded Node.js script with the provided arguments.
     *
     * @param resourcePath classpath resource of the script (for example {@code
     *     /bridges/ajv/ajv-validate.js})
     * @param schemaPath absolute path to the schema file passed to the script
     * @param targetPath absolute path to the JSON/YAML file being validated
     * @return execution result containing exit code and captured output streams
     */
    public Result executeScript(String resourcePath, String schemaPath, String targetPath) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(schemaPath, "schemaPath");
        Objects.requireNonNull(targetPath, "targetPath");

        try {
            Path scriptPath = materializeScript(resourcePath);
            List<String> command = buildCommand(scriptPath, schemaPath, targetPath);
            ProcessExec.Result result = ProcessExec.run(context.root(), timeout, command, Map.of());
            return new Result(result.exitCode(), result.out(), result.err());
        } catch (IOException ioe) {
            log.error("Failed to execute Node bridge script {}", resourcePath, ioe);
            return new Result(1, "", ioe.getMessage());
        }
    }

    private Path materializeScript(String resourcePath) throws IOException {
        Path tempDir = Files.createTempDirectory("polyfix-node-bridge");
        Path scriptFile = tempDir.resolve(Path.of(resourcePath).getFileName().toString());
        try (InputStream in = NodeBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(in, scriptFile, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!scriptFile.toFile().setExecutable(true)) {
            log.warn("Unable to mark {} as executable", scriptFile);
        }
        return scriptFile;
    }

    private List<String> buildCommand(Path script, String schemaPath, String targetPath) {
        List<String> command = new ArrayList<>();
        command.add(resolveNodeBinary(context.config()));
        command.add(script.toString());
        command.add(schemaPath);
        command.add(targetPath);
        return command;
    }

    private String resolveNodeBinary(PolyfixConfig config) {
        if (config != null && config.tools() != null) {
            String nodeValue = config.tools().node();
            if (nodeValue != null && !nodeValue.isBlank()) {
                return nodeValue;
            }
        }
        return "node";
    }
}
