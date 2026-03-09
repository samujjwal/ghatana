package com.ghatana.refactorer.diagnostics.python;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs ruff for Python code analysis and formatting.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * RuffRunner runner = new RuffRunner(context);
 * List<UnifiedDiagnostic> diagnostics = runner.run();
 * }</pre>
 * 
 * @doc.type runner
 * @doc.language python
 * @doc.tool ruff
 
 * @doc.purpose Handles ruff runner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class RuffRunner {
    private static final Logger logger = LoggerFactory.getLogger(RuffRunner.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    private final PolyfixProjectContext context;
    private final ProcessRunner processRunner;
    private boolean fix = false;
    private List<String> includePatterns = List.of(".");
    private List<String> ignorePatterns = List.of("**/.venv/**", "**/venv/**", "**/node_modules/**");

    public RuffRunner(PolyfixProjectContext context) {
        this(context, new ProcessRunner(context));
    }

    RuffRunner(PolyfixProjectContext context, ProcessRunner processRunner) {
        this.context = Objects.requireNonNull(context, "context");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public RuffRunner withFix(boolean fix) {
        this.fix = fix;
        return this;
    }

    public RuffRunner withIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns != null ? includePatterns : List.of(".");
        return this;
    }

    public RuffRunner withIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns != null ? ignorePatterns : List.of();
        return this;
    }

    /**
     * Runs ruff analysis on the specified project root.
     * 
     * @param projectRoot The project root directory to analyze
     * @return Promise resolving to list of unified diagnostics
     * @doc.type method
     * @doc.promise ActiveJ Promise for async ruff execution
     */
    public Promise<List<UnifiedDiagnostic>> run(Path projectRoot) {
        return Promise.ofBlocking(context.exec(), () -> {
            try {
                List<String> command = buildRuffCommand();

                logger.debug("Running ruff: {}", String.join(" ", command));
                ProcessResult result = processRunner.execute(
                        command.get(0),
                        command.subList(1, command.size()),
                        projectRoot,
                        true);

                if (result.exitCode() != 0 && !result.output().trim().startsWith("[")) {
                    logger.error("Ruff failed: {}", result.error());
                    return List.<UnifiedDiagnostic>of();
                }

                List<RuffDiagnostic> ruffDiagnostics = parseRuffOutput(result.output());
                return ruffDiagnostics.stream()
                        .map(this::toUnifiedDiagnostic)
                        .toList();
            } catch (Exception e) {
                logger.error("Error running ruff", e);
                return List.<UnifiedDiagnostic>of();
            }
        });
    }

    /**
     * Convenience overload used by existing tests.
     * 
     * @return Promise resolving to list of unified diagnostics for context root
     * @doc.type method
     * @doc.promise ActiveJ Promise for async ruff execution on context root
     */
    public Promise<List<UnifiedDiagnostic>> run() {
        return run(context.root());
    }

    public String id() {
        return "ruff";
    }

    public boolean isAvailable() {
        try {
            ProcessResult check = processRunner.execute("ruff", List.of("--version"), context.root(), true);
            return check.exitCode() == 0;
        } catch (Exception e) {
            logger.debug("Ruff availability check failed", e);
            return false;
        }
    }

    private List<String> buildRuffCommand() {
        List<String> command = new ArrayList<>();
        command.add("ruff");
        command.add("check");
        command.add("--format=json");

        if (fix) {
            command.add("--fix");
        }

        // Add include patterns
        includePatterns.forEach(
                pattern -> {
                    command.add(pattern);
                });

        // Add ignore patterns
        if (!ignorePatterns.isEmpty()) {
            command.add("--exclude");
            command.add(String.join(",", ignorePatterns));
        }

        return command;
    }

    private List<RuffDiagnostic> parseRuffOutput(String output) {
        try {
            String trimmed = output.trim();
            if (trimmed.isEmpty()) {
                return List.of();
            }
            return objectMapper.readValue(trimmed, new TypeReference<List<RuffDiagnostic>>() {
            });
        } catch (Exception e) {
            logger.error("Failed to parse ruff output", e);
            return List.of();
        }
    }

    private UnifiedDiagnostic toUnifiedDiagnostic(RuffDiagnostic ruffDiag) {
        String file = ruffDiag.getLocation() != null ? ruffDiag.getLocation().getFile() : null;
        int line = ruffDiag.getLocation() != null ? ruffDiag.getLocation().getLine() : 1;
        int column = ruffDiag.getLocation() != null ? ruffDiag.getLocation().getColumn() : 1;
        int endLine = ruffDiag.getEndLocation() != null ? ruffDiag.getEndLocation().getLine() : line;
        int endColumn = ruffDiag.getEndLocation() != null
                ? ruffDiag.getEndLocation().getColumn()
                : Math.max(column + 1, 1);
        return UnifiedDiagnostic.builder()
                .ruleId(ruffDiag.getCode() != null ? ruffDiag.getCode() : "")
                .code(ruffDiag.getCode())
                .message(ruffDiag.getMessage() != null ? ruffDiag.getMessage() : "")
                .file(Path.of(file))
                .startLine(line)
                .startColumn(column)
                .endLine(endLine)
                .endColumn(endColumn)
                .tool("ruff")
                .build();
    }

    // Inner classes for JSON parsing
    @lombok.Data
    private static class RuffDiagnostic {
        private String code;
        private String message;
        private Location location;
        private Location endLocation;
        private Fix fix;
    }

    @lombok.Data
    private static class Location {
        private String file;
        private int line;
        private int column;
    }

    @lombok.Data
    private static class Fix {
        private String message;
        private List<Edit> edits;
    }

    @lombok.Data
    private static class Edit {
        private Location location;
        private Location endLocation;
        private String content;
    }
}
