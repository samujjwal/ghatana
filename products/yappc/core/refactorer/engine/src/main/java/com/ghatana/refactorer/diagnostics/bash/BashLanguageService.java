package com.ghatana.refactorer.diagnostics.bash;

import com.ghatana.refactorer.codemods.bash.BashCodemods;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ Promise-based language service for Bash, coordinating Shellcheck and shfmt.
 *
 * @doc.type service
 * @doc.language bash
 * @doc.tools shellcheck,shfmt
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.purpose Handles bash language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class BashLanguageService {
    private static final Logger logger = LoggerFactory.getLogger(BashLanguageService.class);

    private final PolyfixProjectContext context;
    private final ShellcheckRunner shellcheckRunner;
    private final BashCodemods codemods;
    private final ProcessRunner processRunner;
    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private List<String> includePatterns = List.of(".sh", ".bash");
    private List<String> ignorePatterns = List.of("**/node_modules/**", "**/vendor/**", "**/target/**");
    private boolean fix = false;

    /**
     * Creates a new BashLanguageService with the current thread's Reactor.
     */
    public BashLanguageService(PolyfixProjectContext context) {
        this(context, Reactor.getCurrentReactor());
    }

    /**
     * Creates a new BashLanguageService with the specified Reactor.
     *
     * @param context the project context
     * @param reactor the Reactor to use for async operations
     */
    public BashLanguageService(PolyfixProjectContext context, Reactor reactor) {
        this.context = Objects.requireNonNull(context);
        this.reactor = reactor;
        this.shellcheckRunner = new ShellcheckRunner(context);
        this.codemods = new BashCodemods(context);
        this.processRunner = new ProcessRunner(context);
    }

    public String getLanguageId() {
        return "bash";
    }

    public List<String> getFilePatterns() {
        return includePatterns;
    }

    public BashLanguageService withFilePatterns(List<String> patterns) {
        this.includePatterns = new ArrayList<>(patterns);
        return this;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public BashLanguageService withIgnorePatterns(List<String> patterns) {
        this.ignorePatterns = new ArrayList<>(patterns);
        return this;
    }

    public boolean isFixEnabled() {
        return fix;
    }

    public BashLanguageService withFix(boolean fix) {
        this.fix = fix;
        return this;
    }

    /**
     * Runs diagnostics on a single Bash file asynchronously.
     *
     * @param file the file to analyze
     * @return a Promise resolving to a list of diagnostics
     */
    public Promise<List<UnifiedDiagnostic>> runDiagnostics(Path file) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

                // Run shellcheck for static analysis
                if (shellcheckRunner.isShellcheckAvailable()) {
                    diagnostics.addAll(shellcheckRunner.run(file));
                } else {
                    logger.warn("shellcheck is not available. Skipping Bash file analysis.");
                }

                // Apply fixes if enabled
                if (fix && !diagnostics.isEmpty()) {
                    applyFixes(file, diagnostics);
                }

                return diagnostics;

            } catch (Exception e) {
                logger.error("Error running Bash diagnostics on " + file, e);
                return Collections.emptyList();
            }
        });
    }

    private void applyFixes(Path file, List<UnifiedDiagnostic> diagnostics) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file));
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
                java.nio.file.Files.write(file, fixedContent.getBytes());
            }

        } catch (Exception e) {
            logger.error("Error applying fixes to " + file, e);
        }
    }

    public boolean isAvailable() {
        return shellcheckRunner.isShellcheckAvailable() || isShfmtAvailable();
    }

    private boolean isShfmtAvailable() {
        try {
            Process process = new ProcessBuilder("shfmt", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
