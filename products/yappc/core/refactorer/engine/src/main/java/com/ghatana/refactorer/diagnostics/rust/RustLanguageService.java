package com.ghatana.refactorer.diagnostics.rust;

import com.ghatana.refactorer.codemods.rust.RustCodemods;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ Promise-based language service for Rust, coordinating cargo check, clippy, and rustfmt.
 *
 * @doc.type service
 * @doc.language rust
 * @doc.tools cargo,clippy,rustfmt
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.purpose Handles rust language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class RustLanguageService {
    private static final Logger logger = LoggerFactory.getLogger(RustLanguageService.class);

    private final PolyfixProjectContext context;
    private final CargoRunner cargoRunner;
    private final ClippyRunner clippyRunner;
    private final RustCodemods codemods;
    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private List<String> includePatterns = List.of(".rs");
    private List<String> ignorePatterns = List.of("**/target/**", "**/node_modules/**");
    private boolean fix = false;

    /**
     * Creates a new RustLanguageService with the current thread's Reactor.
     */
    public RustLanguageService(PolyfixProjectContext context) {
        this(context, Reactor.getCurrentReactor());
    }

    /**
     * Creates a new RustLanguageService with the specified Reactor.
     *
     * @param context the project context
     * @param reactor the Reactor to use for async operations
     */
    public RustLanguageService(PolyfixProjectContext context, Reactor reactor) {
        this.context = Objects.requireNonNull(context);
        this.reactor = reactor;
        this.cargoRunner = new CargoRunner(context);
        this.clippyRunner = new ClippyRunner(context);
        this.codemods = new RustCodemods(context);
    }

    public String getLanguageId() {
        return "rust";
    }

    public List<String> getFilePatterns() {
        return includePatterns;
    }

    public RustLanguageService withFilePatterns(List<String> patterns) {
        this.includePatterns = new ArrayList<>(patterns);
        return this;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public RustLanguageService withIgnorePatterns(List<String> patterns) {
        this.ignorePatterns = new ArrayList<>(patterns);
        return this;
    }

    public boolean isFixEnabled() {
        return fix;
    }

    public RustLanguageService withFix(boolean fix) {
        this.fix = fix;
        return this;
    }

    /**
     * Runs diagnostics on a single Rust file asynchronously.
     *
     * @param file the file to analyze
     * @return a Promise resolving to a list of diagnostics
     */
    public Promise<List<UnifiedDiagnostic>> runDiagnostics(Path file) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                Path cargoDir = findCargoRoot(file);
                if (cargoDir == null) {
                    logger.debug("No Cargo.toml found for {}", file);
                    return List.of();
                }

                List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

                // Run cargo check
                if (cargoRunner.isCargoAvailable()) {
                    diagnostics.addAll(cargoRunner.run(cargoDir));
                } else {
                    logger.warn("cargo is not available. Skipping Rust analysis.");
                }

                // Run clippy if available
                if (clippyRunner.isClippyAvailable()) {
                    diagnostics.addAll(clippyRunner.run(cargoDir));
                } else {
                    logger.info(
                            "cargo clippy is not available. Some Rust lints may be missed.");
                }

                // Filter diagnostics for the current file
                List<UnifiedDiagnostic> fileDiagnostics = diagnostics.stream()
                        .filter(d -> d.getFile().equals(file))
                        .collect(Collectors.toList());

                // Apply fixes if enabled
                if (fix && !fileDiagnostics.isEmpty()) {
                    applyFixes(file, fileDiagnostics);
                }

                return fileDiagnostics;

            } catch (Exception e) {
                logger.error("Error running Rust diagnostics on " + file, e);
                return List.of();
            }
        });
    }

    private Path findCargoRoot(Path file) {
        Path current = file.getParent();
        while (current != null) {
            if (Files.exists(current.resolve("Cargo.toml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void applyFixes(Path file, List<UnifiedDiagnostic> diagnostics) {
        try {
            String content = new String(Files.readAllBytes(file));
            String fixedContent = content;

            // Group diagnostics by line for more efficient processing
            Map<Integer, List<UnifiedDiagnostic>> diagnosticsByLine = diagnostics.stream()
                    .filter(d -> d.getRuleId() != null)
                    .collect(Collectors.groupingBy(UnifiedDiagnostic::getStartLine));

            // Apply fixes for each line with diagnostics
            for (Map.Entry<Integer, List<UnifiedDiagnostic>> entry : diagnosticsByLine.entrySet()) {
                int lineNum = entry.getKey();
                List<UnifiedDiagnostic> lineDiagnostics = entry.getValue();

                for (UnifiedDiagnostic diagnostic : lineDiagnostics) {
                    try {
                        String ruleId = diagnostic.getRuleId();
                        String fix = codemods.getFix(ruleId, content, diagnostic);

                        if (fix != null && !fix.equals(content)) {
                            fixedContent = fix;
                            content = fixedContent; // Update content for subsequent fixes

                            logger.debug(
                                    "Applied fix for {} in {}:{} (rule: {})",
                                    file.getFileName(),
                                    lineNum,
                                    diagnostic.getStartColumn(),
                                    ruleId);
                        }
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to apply fix for {}: {}",
                                diagnostic.getRuleId(),
                                e.getMessage());
                    }
                }
            }

            // Write fixed content back to file if changes were made
            if (!fixedContent.equals(content)) {
                Files.write(file, fixedContent.getBytes());

                // Format the file after applying fixes
                if (isRustfmtAvailable()) {
                    formatFile(file);
                }
            }

        } catch (Exception e) {
            logger.error("Error applying fixes to " + file, e);
        }
    }

    private void formatFile(Path file) {
        try {
            Process process = new ProcessBuilder("rustfmt", file.toString()).inheritIO().start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("rustfmt exited with code {}", exitCode);
            }
        } catch (Exception e) {
            logger.warn("Failed to run rustfmt on " + file, e);
        }
    }

    public boolean isAvailable() {
        return cargoRunner.isCargoAvailable() || isRustfmtAvailable();
    }

    private boolean isRustfmtAvailable() {
        try {
            Process process = new ProcessBuilder("rustfmt", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
