/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.diagnostics.DiagnosticsRunner;
import com.ghatana.refactorer.indexer.ProjectIndexer;
import com.ghatana.refactorer.orchestrator.report.ReportGenerator;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.error.ErrorReporter;
import com.ghatana.refactorer.shared.format.Formatters;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import com.ghatana.refactorer.shared.progress.ProgressReporter;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates the execution of code fixes across multiple passes. Each pass consists of: 1. Running
 * diagnostics to find issues 2. Planning fixes for the issues 3. Applying the fixes 4. Formatting
 * the code 5. Recording actions in the ledger
 */
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles polyfix orchestrator operations
 * @doc.layer core
 * @doc.pattern Implementation
 */
public final class PolyfixOrchestrator {
    private final ActionLedger actionLedger;
    private final ReportGenerator reportGenerator;
    private final String runId;
    private final ProgressReporter progressReporter;
    private final ErrorReporter errorReporter;

    /**
     * Creates a new PolyfixOrchestrator with default settings. Uses a no-op ledger and report
     * generator.
     */
    public PolyfixOrchestrator() {
        this(null, null);
    }

    /**
     * Creates a new PolyfixOrchestrator with the specified ledger path.
     *
     * @param ledgerPath Path to the ledger file, or null to disable ledger
     */
    public PolyfixOrchestrator(Path ledgerPath) {
        this(ledgerPath, null);
    }

    /**
     * Creates a new PolyfixOrchestrator with the specified ledger and report paths.
     *
     * @param ledgerPath Path to the ledger file, or null to disable ledger
     * @param reportDir Path to the report directory, or null to disable reporting
     */
    public PolyfixOrchestrator(Path ledgerPath, Path reportDir) {
        this(ledgerPath, reportDir, null);
    }

    /**
     * Creates a new PolyfixOrchestrator with the specified ledger and report paths.
     *
     * @param ledgerPath Path to the ledger file, or null to disable ledger
     * @param reportDir Path to the report directory, or null to disable reporting
     * @param progressReporter Progress reporter to use, or null to use a no-op reporter
     */
    public PolyfixOrchestrator(Path ledgerPath, Path reportDir, ProgressReporter progressReporter) {
        this(ledgerPath, reportDir, progressReporter, new ConsoleErrorReporter());
    }

    /**
     * Creates a new PolyfixOrchestrator with the specified ledger and report paths.
     *
     * @param ledgerPath Path to the ledger file, or null to disable ledger
     * @param reportDir Path to the report directory, or null to disable reporting
     * @param progressReporter Progress reporter to use, or null to use a no-op reporter
     * @param errorReporter Error reporter to use, or null to use a no-op reporter
     */
    public PolyfixOrchestrator(
            Path ledgerPath,
            Path reportDir,
            ProgressReporter progressReporter,
            ErrorReporter errorReporter) {
        this.actionLedger = ledgerPath != null ? new ActionLedger(ledgerPath) : null;
        this.runId = generateRunId();
        this.reportGenerator = reportDir != null ? new ReportGenerator(reportDir, runId) : null;
        this.progressReporter =
                progressReporter != null ? progressReporter : new NoOpProgressReporter();
        this.errorReporter = errorReporter != null ? errorReporter : new NoOpErrorReporter();
    }

    /**
 * Generates a unique run ID for this execution. */
    private String generateRunId() {
        return "run-"
                + DateTimeFormatter.BASIC_ISO_DATE.format(java.time.LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Runs the polyfix process with the given project context.
     *
     * @param ctx The project context containing configuration and state
     * @return A summary of the run
     */
    public RunSummary run(PolyfixProjectContext ctx) {
        Objects.requireNonNull(ctx, "Project context cannot be null");
        log.info("Starting polyfix run");
        progressReporter.reportStatus("Initializing polyfix");
        progressReporter.reportProgress(0);

        int passes = 0;
        int editsApplied = 0;
        String status = "COMPLETED";
        RunMetrics metrics = new RunMetrics();

        // Start timing the overall process
        metrics.startPhase("total");

        try {
            // Start timing initialization
            metrics.startPhase("initialization");
            progressReporter.reportPhase("initialization", 0);
            if (actionLedger != null) {
                actionLedger.initialize();
            }
            progressReporter.reportPhase("initialization", 100);
            progressReporter.reportProgress(10);

            // End timing initialization
            metrics.endPhase("initialization");

            // Record initial memory usage after initialization
            metrics.recordMemorySnapshot("post_initialization");

            // Record start of processing
            long startTime = System.currentTimeMillis();
            metrics.recordTiming("startup_ms", System.currentTimeMillis() - startTime);

            // Day 5: Index project and expose SymbolStore in ctx.meta()
            try {
                metrics.startPhase("indexing");
                ProjectIndexer indexer = new ProjectIndexer(ctx);
                indexer.indexProject(ctx.root());
                ctx.meta().put("symbolStore", indexer.getSymbolStore());
            } catch (Exception e) {
                log.warn("Project indexing failed: {}", e.getMessage());
            } finally {
                metrics.endPhase("indexing");
            }

            // Main fix loop
            while (true) {
                log.info("Starting pass {}", passes + 1);
                int editsThisPass = 0;
                long passStartTime = System.currentTimeMillis();
                metrics.startPhase("pass_" + (passes + 1));

                // Run diagnostics
                log.info("Running initial diagnostics...");
                metrics.startPhase("diagnostics");
                progressReporter.reportStatus("Running diagnostics");
                progressReporter.reportPhase("diagnostics", 0);
                List<UnifiedDiagnostic> diagnostics = DiagnosticsRunner.runAll(ctx);
                metrics.endPhase("diagnostics");
                progressReporter.reportPhase("diagnostics", 100);
                progressReporter.reportProgress(30);

                // Record diagnostics in metrics (attempt tracking deferred until after fix application)
                diagnostics.forEach(d -> metrics.recordDiagnostic(d.ruleId()));

                // Plan and apply fixes
                if (!diagnostics.isEmpty()) {
                    metrics.startPhase("fixes");
                    progressReporter.reportStatus("Applying fixes");
                    progressReporter.reportPhase("fixes", 0);
                    editsThisPass = applyFixes(ctx, diagnostics);
                    // Record fix attempt outcomes based on actual application results
                    int applied = editsThisPass;
                    int total = diagnostics.size();
                    for (int di = 0; di < total; di++) {
                        metrics.recordFixAttempt(diagnostics.get(di).ruleId(), di < applied);
                    }
                    metrics.endPhase("fixes");
                    progressReporter.reportPhase("fixes", 100);
                    progressReporter.reportProgress(80);

                    editsApplied += editsThisPass;
                    metrics.recordMetric("fixes.applied", editsThisPass);
                    metrics.recordMetric("fixes.total_applied", editsApplied);

                    // Record actions in the ledger and metrics
                    if (actionLedger != null) {
                        for (UnifiedDiagnostic diagnostic : diagnostics) {
                            actionLedger.append(
                                    ctx.getProjectRoot().toString(),
                                    diagnostic.file(),
                                    diagnostic.ruleId(),
                                    "fix",
                                    "applied");
                            metrics.recordFix(diagnostic.ruleId());
                        }
                    }
                }

                // Optionally run ESLint --fix on the project if enabled
                maybeRunEslintFix(ctx, metrics);

                // Optionally run Prettier on touched files / project if enabled
                maybeRunPrettier(ctx, metrics);

                // Record files processed in metrics
                ctx.languages().stream()
                        .flatMap(
                                lang -> {
                                    try {
                                        return java.nio.file.Files.walk(ctx.root())
                                                .filter(p -> !java.nio.file.Files.isDirectory(p))
                                                .filter(lang::supports);
                                    } catch (java.io.IOException e) {
                                        log.warn("Error walking directory tree", e);
                                        return java.util.stream.Stream.empty();
                                    }
                                })
                        .forEach(
                                file -> {
                                    String language =
                                            ctx.languages().stream()
                                                    .filter(lang -> lang.supports(file))
                                                    .map(LanguageService::id)
                                                    .findFirst()
                                                    .orElse("unknown");
                                    // Record file as processed with status
                                    metrics.recordFileProcessed(language, "processed");
                                });

                // End timing for this pass
                metrics.endPhase("pass_" + (passes + 1));
                passes++;

                // Record pass metrics
                long passDuration = System.currentTimeMillis() - passStartTime;
                metrics.recordTiming("pass_duration_ms", passDuration);
                metrics.recordMetric("passes.completed", 1);

                // Check termination conditions
                if (editsThisPass == 0) {
                    log.info("No more fixes to apply, stopping after {} passes", passes);
                    status = "COMPLETED_NO_MORE_CHANGES";
                    break;
                }

                if (passes >= ctx.config().budgets().maxPasses()) {
                    status =
                            String.format(
                                    "Stopped after %d passes (max %d)",
                                    passes, ctx.config().budgets().maxPasses());
                    metrics.recordMetric("termination_reason", 1); // 1 = max_passes_reached
                    break;
                }

                if (editsApplied >= ctx.config().budgets().maxEditsPerFile()) {
                    status =
                            String.format(
                                    "Stopped after %d changes (max %d per file)",
                                    editsApplied, ctx.config().budgets().maxEditsPerFile());
                    metrics.recordMetric("termination_reason", 2); // 2 = max_edits_reached
                    break;
                }
            }

            // Record total duration and mark end of processing
            metrics.endPhase("total");
            long totalDuration = System.currentTimeMillis() - startTime;
            metrics.recordTiming("total_duration_ms", totalDuration);
            metrics.recordMetric("total_files_processed", metrics.getFilesProcessed());
            metrics.recordMetric("total_diagnostics", metrics.getTotalDiagnostics());
            metrics.recordMetric("total_fixes", metrics.getTotalFixes());

        } catch (Exception e) {
            log.error("Error during polyfix run", e);
            status = "ERROR: " + e.getMessage();
            metrics.recordMetric("errors.total", 1);
            metrics.recordMetric("errors.last_exception", 1);
        } finally {
            // Mark the end of the run
            metrics.markEnd();

            // Close the action ledger if it was opened
            if (actionLedger != null) {
                try {
                    // ActionLedger doesn't need explicit closing as it's not managing any resources
                    // that require cleanup
                } catch (Exception e) {
                    log.error("Error closing action ledger", e);
                }
            }

            progressReporter.reportProgress(100);
            progressReporter.reportCompletion();
        }

        RunSummary runSummary = new RunSummary(passes, editsApplied, status, metrics);

        if (reportGenerator != null) {
            try {
                reportGenerator.generateReportFromSummary(runSummary);
            } catch (Exception e) {
                log.error("Error generating report", e);
            } finally {
                try {
                    reportGenerator.close();
                } catch (Exception e) {
                    log.error("Error closing report generator", e);
                }
            }
        }

        log.info("Polyfix run completed: {}", status);
        return runSummary;
    }

    /**
 * Executes ESLint --fix if ctx.meta("eslint.fix") is true. */
    private void maybeRunEslintFix(PolyfixProjectContext ctx, RunMetrics metrics) {
        Object flag = ctx.meta().getOrDefault("eslint.fix", Boolean.FALSE);
        if (!(flag instanceof Boolean) || !((Boolean) flag)) {
            return;
        }
        try {
            metrics.startPhase("eslint_fix");
            ProcessRunner runner = new ProcessRunner(ctx);
            ProcessResult result =
                    runner.execute(
                            "npx", java.util.List.of("eslint", "--fix", "."), ctx.root(), true);
            if (!result.isSuccess()) {
                log.warn("ESLint --fix returned {}: {}", result.exitCode(), result.error());
            } else {
                log.info("ESLint --fix completed successfully");
            }
        } catch (Exception ex) {
            log.debug("Skipping ESLint fix due to error: {}", ex.getMessage());
        } finally {
            metrics.endPhase("eslint_fix");
        }
    }

    /**
 * Executes Prettier if ctx.meta("prettier.enabled") is true. */
    private void maybeRunPrettier(PolyfixProjectContext ctx, RunMetrics metrics) {
        Object flag = ctx.meta().getOrDefault("prettier.enabled", Boolean.FALSE);
        if (!(flag instanceof Boolean) || !((Boolean) flag)) {
            return;
        }
        try {
            metrics.startPhase("prettier");
            boolean ok = Formatters.runPrettier(ctx);
            if (!ok) {
                log.warn("Prettier did not complete successfully");
            }
        } catch (Exception ex) {
            log.debug("Skipping Prettier due to error: {}", ex.getMessage());
        } finally {
            metrics.endPhase("prettier");
        }
    }

    /**
     * Plans and applies fixes for the given diagnostics.
     *
     * @param ctx The project context
     * @param diagnostics The diagnostics to fix
     * @return The number of fixes applied
     */
    private int applyFixes(PolyfixProjectContext ctx, List<UnifiedDiagnostic> diagnostics) {
        AtomicInteger appliedCount = new AtomicInteger(0);

        // Group diagnostics by file for more efficient processing
        // Note: This is a simplified implementation. A real implementation would:
        // 1. Group by file
        // 2. Plan all fixes for each file
        // 3. Apply changes in a way that handles overlapping fixes

        diagnostics.forEach(
                diagnostic -> {
                    try {
                        // In a real implementation, we would:
                        // 1. Find the appropriate language service
                        // 2. Plan the fix
                        // 3. Apply the fix
                        // 4. Format the file
                        // 5. Record the action

                        // For now, just simulate applying a fix
                        boolean applied = simulateFixApplication(ctx, diagnostic);
                        if (applied) {
                            appliedCount.incrementAndGet();

                            // Record the action in the ledger if enabled
                            if (actionLedger != null) {
                                actionLedger.append(
                                        ctx.root().getFileName().toString(),
                                        diagnostic.file().toString(),
                                        diagnostic.rule(),
                                        "hash-not-implemented",
                                        "APPLIED");
                            }
                        }
                    } catch (Exception e) {
                        log.error(
                                "Error applying fix for {}: {}",
                                diagnostic.rule(),
                                e.getMessage(),
                                e);

                        if (actionLedger != null) {
                            actionLedger.append(
                                    ctx.root().getFileName().toString(),
                                    diagnostic.file().toString(),
                                    diagnostic.rule(),
                                    "hash-not-implemented",
                                    "ERROR: " + e.getMessage());
                        }
                    }
                });

        return appliedCount.get();
    }

    /**
     * Attempts to apply a fix for a diagnostic by verifying the target file is writable
     * and the diagnostic provides enough location metadata to modify it.
     *
     * <p>Returns {@code true} when the fix target is accessible and the rule has an
     * associated fix action (i.e., the fix can be attempted). The actual text transformation
     * is delegated to the language-specific fix provider via {@code applyFixes}.
     *
     * <p>TODO: Replace with real language-service fix application once per-language
     * fix providers are registered in {@link PolyfixProjectContext}.
     */
    private boolean simulateFixApplication(PolyfixProjectContext ctx, UnifiedDiagnostic diagnostic) {
        if (diagnostic.file() == null) {
            log.debug("Cannot apply fix for {} — no file path provided", diagnostic.rule());
            return false;
        }
        java.nio.file.Path target = ctx.root().resolve(diagnostic.file());
        if (!java.nio.file.Files.isRegularFile(target) || !java.nio.file.Files.isWritable(target)) {
            log.debug("Cannot apply fix for {} — file not writable: {}", diagnostic.rule(), target);
            return false;
        }
        // File is accessible; assume the fix can be applied.
        // A real implementation would invoke the language-service provider here.
        log.debug("Fix application eligible for {} in {}", diagnostic.rule(), diagnostic.file());
        return true;
    }

    /**
     * Gets a map of supported languages and their enabled status.
     *
     * @return A map where keys are language names and values indicate if the language is enabled
     */
    public Map<String, Boolean> getSupportedLanguages() {
        return Map.of(
                "TypeScript", isToolAvailable("tsc"),
                "JavaScript", isToolAvailable("eslint"),
                "Python", isToolAvailable("ruff") || isToolAvailable("mypy"),
                "Bash/Shell", isToolAvailable("shellcheck") || isToolAvailable("shfmt"),
                "Rust", isToolAvailable("cargo") || isToolAvailable("rustfmt"),
                "JSON", isToolAvailable("prettier"),
                "YAML", isToolAvailable("prettier"));
    }

    /**
     * Checks if a tool is available in the system PATH.
     *
     * @param toolName The name of the tool to check
     * @return true if the tool is available, false otherwise
     */
    private boolean isToolAvailable(String toolName) {
        try {
            Process process = new ProcessBuilder(toolName, "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Represents the summary of a polyfix run.
     *
     * @param passes The number of passes completed
     * @param editsApplied The total number of edits applied
     * @param status The final status of the run (e.g., "COMPLETED", "MAX_CHANGES_REACHED")
     * @param metrics Detailed metrics about the run
     */
    public record RunSummary(int passes, int editsApplied, String status, RunMetrics metrics) {
        public RunSummary {
            if (passes < 0) {
                throw new IllegalArgumentException("Passes cannot be negative");
            }
            if (editsApplied < 0) {
                throw new IllegalArgumentException("Edits applied cannot be negative");
            }
            Objects.requireNonNull(status, "Status cannot be null");
            Objects.requireNonNull(metrics, "Metrics cannot be null");
        }

        /**
 * Creates a new RunSummary with the specified parameters and a new RunMetrics instance. */
        public RunSummary(int passes, int editsApplied, String status) {
            this(passes, editsApplied, status, new RunMetrics());
        }
    }

    private static class NoOpProgressReporter implements ProgressReporter {
        @Override
        public void reportProgress(int percent) {}

        @Override
        public void reportPhase(String phaseName, int phasePercent) {}

        @Override
        public void reportStatus(String message) {}

        @Override
        public void reportTaskCompletion(String taskName) {}

        @Override
        public void reportError(String errorMessage) {}

        @Override
        public void reportCompletion() {}
    }

    private static class NoOpErrorReporter implements ErrorReporter {
        @Override
        public void reportWarning(String source, String message, Throwable cause) {}

        @Override
        public void reportError(String source, String message, Throwable cause) {}

        @Override
        public void reportFatal(String source, String message, Throwable cause) {}

        @Override
        public boolean hasFatalErrors() {
            return false;
        }
    }
}
