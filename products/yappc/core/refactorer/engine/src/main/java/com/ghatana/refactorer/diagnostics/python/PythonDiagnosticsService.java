package com.ghatana.refactorer.diagnostics.python;

import com.ghatana.refactorer.codemods.python.PythonCodemods;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.Objects;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Language service for Python, coordinating Ruff and Black.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * PythonDiagnosticsService service = new PythonDiagnosticsService(context);
 * List<UnifiedDiagnostic> diagnostics = service.runDiagnostics().join();
 * }</pre>
 * 
 * @doc.type service
 * @doc.language python
 * @doc.tools ruff,black
 
 * @doc.purpose Handles python diagnostics service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class PythonDiagnosticsService {
    private static final Logger logger = LoggerFactory.getLogger(PythonDiagnosticsService.class);

    private final PolyfixProjectContext context;
    private final RuffRunner ruffRunner;
    private final BlackRunner blackRunner;
    private final PythonCodemods codemods;
    private final ProcessRunner processRunner;

    private List<String> includePatterns = List.of(".");
    private List<String> ignorePatterns = List.of("**/.venv/**", "**/venv/**", "**/node_modules/**");
    private boolean fix = false;
    private boolean format = true;

    public PythonDiagnosticsService(PolyfixProjectContext context) {
        this(
                context,
                new RuffRunner(context),
                new BlackRunner(context),
                new PythonCodemods(context),
                new ProcessRunner(context));
    }

    PythonDiagnosticsService(
            PolyfixProjectContext context,
            RuffRunner ruffRunner,
            BlackRunner blackRunner,
            PythonCodemods codemods,
            ProcessRunner processRunner) {
        this.context = Objects.requireNonNull(context, "context");
        this.ruffRunner = Objects.requireNonNull(ruffRunner, "ruffRunner");
        this.blackRunner = Objects.requireNonNull(blackRunner, "blackRunner");
        this.codemods = Objects.requireNonNull(codemods, "codemods");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public String getLanguageId() {
        return "python";
    }

    public PythonDiagnosticsService withFix(boolean fix) {
        this.fix = fix;
        return this;
    }

    public PythonDiagnosticsService withFormat(boolean format) {
        this.format = format;
        return this;
    }

    public PythonDiagnosticsService withIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns != null ? includePatterns : List.of(".");
        this.ruffRunner.withIncludePatterns(includePatterns);
        this.blackRunner.withIncludePatterns(includePatterns);
        return this;
    }

    public PythonDiagnosticsService withIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns != null ? ignorePatterns : List.of();
        this.ruffRunner.withIgnorePatterns(ignorePatterns);
        this.blackRunner.withIgnorePatterns(ignorePatterns);
        return this;
    }

    /**
     * Runs diagnostics using Ruff and optionally Black for formatting.
     * 
     * @return Promise resolving to list of unified diagnostics
     * @doc.type method
     * @doc.promise ActiveJ Promise for async diagnostics execution
     */
    public Promise<List<UnifiedDiagnostic>> runDiagnostics() {
        return ruffRunner
                .withFix(fix)
                .run(context.root())
                .then(
                        diagnostics -> {
                            if (fix && format) {
                                return blackRunner
                                        .run()
                                        .then(
                                                success -> {
                                                    if (!success) {
                                                        logger.warn("Black formatting completed with issues");
                                                    }
                                                    return Promise.of(diagnostics);
                                                });
                            }
                            return Promise.of(diagnostics);
                        });
    }

    /**
     * Applies fixes for the given diagnostics.
     * 
     * @param diagnostics List of diagnostics to fix
     * @return Promise resolving to true if all fixes applied successfully
     * @doc.type method
     * @doc.promise ActiveJ Promise for async fix application
     */
    public Promise<Boolean> applyFixes(List<UnifiedDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return Promise.of(true);
        }

        final Path root = context.root().toAbsolutePath().normalize();

        // Group diagnostics by a stable, normalized absolute path
        Map<Path, List<UnifiedDiagnostic>> diagnosticsByFile = diagnostics.stream()
                .collect(
                        Collectors.groupingBy(
                                d -> {
                                    Path p = Paths.get(d.getFile().toString());
                                    Path abs = p.isAbsolute() ? p : root.resolve(p);
                                    return abs.normalize();
                                }));

        // Reasonable per-file timeout (tune as needed / make configurable)
        Duration perFileTimeout = Duration.ofSeconds(30);

        List<Promise<Boolean>> fixPromises = diagnosticsByFile.entrySet().stream()
                .map(entry -> {
                    Path file = entry.getKey();
                    List<UnifiedDiagnostic> fileDiagnostics = entry.getValue();
                    return Promise.ofBlocking(context.exec(), () -> {
                        try {
                            return codemods.applyCodemods(file, fileDiagnostics);
                        } catch (Exception e) {
                            logger.error("Failed to apply fixes to {}", file, e);
                            return false;
                        }
                    });
                })
                .collect(Collectors.toList());

        // Combine all fix promises - return true only if all succeed
        return Promises.toList(fixPromises)
                .map(results -> results.stream().allMatch(Boolean::booleanValue));
    }

    /**
     * Formats files using Black.
     * 
     * @return Promise resolving to true if formatting succeeded
     * @doc.type method
     * @doc.promise ActiveJ Promise for async Black formatting
     */
    public Promise<Boolean> formatFiles() {
        return blackRunner.run();
    }

    public boolean isAvailable() {
        try {
            ProcessResult ruffCheck = processRunner.execute("ruff", List.of("--version"), context.root(), true);
            ProcessResult blackCheck = processRunner.execute("black", List.of("--version"), context.root(), true);
            return ruffCheck.exitCode() == 0 && blackCheck.exitCode() == 0;
        } catch (Exception e) {
            logger.debug("Python tools check failed", e);
            return false;
        }
    }
}
