/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandLine.Command(name = "run", description = "Run orchestrator loop")
/**
 * @doc.type class
 * @doc.purpose Handles run command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public final class RunCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);
    @CommandLine.ParentCommand PolyfixCommand parent;

    @CommandLine.Option(names = "--max-passes", defaultValue = "3")
    int maxPasses;

    @CommandLine.Option(names = "--dry-run", defaultValue = "false")
    boolean dryRun;

    @CommandLine.Option(
            names = "--ts-js",
            description = "Enable TS/JS pass (placeholder)",
            defaultValue = "true")
    boolean tsJs;

    @CommandLine.Option(
            names = "--fix-imports",
            description = "Enable ESLint --fix after applying fixes",
            defaultValue = "true")
    boolean fixImports;

    @CommandLine.Option(
            names = "--prettier",
            description = "Run Prettier on touched files/project at end of pass",
            defaultValue = "true")
    boolean prettier;

    @CommandLine.Option(
            names = "--memory",
            description = "Enable memory-aware fix planning using action ledger",
            defaultValue = "true")
    boolean memoryEnabled;

    @CommandLine.Option(
            names = "--consistency",
            description = "Enable consistency enforcement and validation",
            defaultValue = "true")
    boolean consistencyEnabled;

    @CommandLine.Option(
            names = "--rollback",
            description = "Enable automatic rollback on regression detection",
            defaultValue = "true")
    boolean rollbackEnabled;

    @CommandLine.Option(
            names = "--oscillation-detection",
            description = "Enable oscillation detection to prevent infinite loops",
            defaultValue = "true")
    boolean oscillationDetection;

    @CommandLine.Option(
            names = "--confidence-threshold",
            description = "Minimum confidence threshold for applying fixes (0.0-1.0)",
            defaultValue = "0.5")
    double confidenceThreshold;

    @CommandLine.Option(
            names = "--languages",
            description =
                    "Comma-separated list of languages to process (python,bash,rust,java,ts,js)",
            defaultValue = "java")
    String languages;

    public void run() {
        Path root = parent.root;
        PolyfixProjectContext ctx = PolyfixCommand.buildContext(root);

        // Set orchestrator meta flags
        ctx.meta().put("eslint.fix", fixImports);
        ctx.meta().put("prettier.enabled", prettier);

        // Set memory and consistency flags
        ctx.meta().put("memory.enabled", memoryEnabled);
        ctx.meta().put("consistency.enabled", consistencyEnabled);
        ctx.meta().put("rollback.enabled", rollbackEnabled);
        ctx.meta().put("oscillation.detection", oscillationDetection);
        ctx.meta().put("confidence.threshold", confidenceThreshold);
        ctx.meta().put("languages", languages);

        // Validate confidence threshold
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            log.error("Error: Confidence threshold must be between 0.0 and 1.0");
            System.exit(1);
        }

        var orchestrator = new PolyfixOrchestrator();
        var summary = orchestrator.run(ctx);
        log.info("Passes={}, EditsApplied={}, Status={}, Memory={}, Consistency={}", summary.passes(),
                summary.editsApplied(),
                summary.status(),
                memoryEnabled ? "ON" : "OFF",
                consistencyEnabled ? "ON" : "OFF");
    }
}
