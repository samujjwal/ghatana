package com.ghatana.refactorer.consistency;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enforces code style and quality standards across the project.
 * Can run in different modes: check-only, fix, or format.
 * 
 * @since 2.0.0 - Migrated to ActiveJ Promise-based API
 
 * @doc.type class
 * @doc.purpose Handles consistency enforcer operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ConsistencyEnforcer {
    private static final Logger logger = LogManager.getLogger(ConsistencyEnforcer.class);

    private final PolyfixProjectContext context;
    private final ConsistencyConfig config;
    private final ActionLedger actionLedger;
    private final FailureFingerprint failureFingerprint;
    private final Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    public ConsistencyEnforcer(PolyfixProjectContext context, ConsistencyConfig config, Reactor reactor) {
        this.context = Objects.requireNonNull(context, "PolyfixProjectContext cannot be null");
        this.config = Objects.requireNonNull(config, "ConsistencyConfig cannot be null");
        this.reactor = Objects.requireNonNull(reactor, "Reactor cannot be null");
        this.actionLedger = new ActionLedger();
        this.failureFingerprint = new FailureFingerprint();
    }

    /**
     * Runs consistency checks on the specified files asynchronously.
     * @param files List of files to check
     * @return Promise that completes with a list of diagnostics
     */
    public Promise<List<UnifiedDiagnostic>> checkFiles(List<Path> files) {
        Objects.requireNonNull(files, "Files to check cannot be null");
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> checkConsistency(files));
    }
    public List<UnifiedDiagnostic> checkConsistency(List<Path> filesToCheck) {
        Objects.requireNonNull(filesToCheck, "Files to check cannot be null");

        List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

        for (Path file : filesToCheck) {
            if (file == null || !Files.exists(file)) {
                continue;
            }

            try {
                ensureFormattingPlaceholder(file, diagnostics);
                checkFileSpecificRules(file, diagnostics);
            } catch (Exception e) {
                diagnostics.add(createErrorDiagnostic(file, "Error checking consistency: " + e.getMessage()));
                logger.warn("Error checking consistency for {}", file, e);
            }
        }

        return diagnostics;
    }

    public List<Path> enforceFormatting(PolyfixProjectContext context, List<Path> filesToFormat) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(filesToFormat, "Files to format cannot be null");

        // Placeholder: formatting integration pending implementation (EPIC upscope).
        logger.info("Formatting enforcement currently no-ops (files: {})", filesToFormat.size());
        return List.of();
    }

    /**
     * Asynchronously applies fixes to the specified files.
     * @param filesToFix List of files to fix
     * @return Promise that completes with a list of fixed file paths
     */
    public Promise<List<Path>> fixFiles(List<Path> filesToFix) {
        Objects.requireNonNull(filesToFix, "Files to fix cannot be null");
        logger.info("Fix mode requested for {} files", filesToFix.size());
        return Promise.of(List.of());
    }

    /**
     * Asynchronously formats the specified files.
     * @param filesToFormat List of files to format
     * @return Promise that completes with a list of formatted file paths
     */
    public Promise<List<Path>> formatFiles(List<Path> filesToFormat) {
        Objects.requireNonNull(filesToFormat, "Files to format cannot be null");
        logger.info("Format mode requested for {} files", filesToFormat.size());
        return Promise.of(List.of());
    }

    private void ensureFormattingPlaceholder(Path file, List<UnifiedDiagnostic> diagnostics) {
        if (!config.isModeEnabled(ConsistencyConfig.Mode.FORMAT)) {
            return;
        }
        diagnostics.add(
                createDiagnostic(
                        file,
                        "Formatting mode enabled but formatter integration is pending",
                        "consistency:format",
                        config.shouldFailOnError() ? Severity.ERROR : Severity.WARNING));
    }

    private void checkFileSpecificRules(Path file, List<UnifiedDiagnostic> diagnostics) {
        // Example: Check for missing newline at end of file
        try {
            List<String> lines = Files.readAllLines(file);
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                diagnostics.add(
                        createDiagnostic(
                                file,
                                "File should end with a newline",
                                "consistency:newline",
                                Severity.WARNING));
            }
        } catch (Exception e) {
            if (context.log() != null) {
                context.log().warn("Error checking file rules: {}", file, e);
            } else {
                logger.warn("Error checking file rules: {}", file, e);
            }
        }
    }

    private UnifiedDiagnostic createErrorDiagnostic(Path file, String message) {
        return createDiagnostic(file, message, "consistency:error", Severity.ERROR);
    }

    private UnifiedDiagnostic createDiagnostic(Path file, String message, String ruleId, Severity severity) {
        return UnifiedDiagnostic.builder()
                .tool("consistency")
                .ruleId(ruleId)
                .message(message)
                .file(file)
                .severity(severity)
                .startLine(1)
                .startColumn(1)
                .build();
    }
}
