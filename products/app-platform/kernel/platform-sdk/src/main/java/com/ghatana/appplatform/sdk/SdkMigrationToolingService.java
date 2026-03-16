package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.*;

/**
 * @doc.type    Service
 * @doc.purpose CLI migration tool: analyse consumer code for breaking API usages between SDK
 *              versions, auto-fix safe migrations, and produce a risk-annotated migration report.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter; Promise.ofBlocking
 *
 * STORY-K12-014: SDK migration tooling
 * CLI invocation: siddhanta sdk migrate --from 1.x --to 2.x
 */
public class SdkMigrationToolingService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface BreakingChangeRegistryPort {
        List<BreakingChange> listChanges(String fromVersion, String toVersion) throws Exception;
    }

    public interface SourceCodeScannerPort {
        /** Scan a source directory and find all usages matching a given pattern. */
        List<CodeUsage> findUsages(String sourceDir, String pattern) throws Exception;
    }

    public interface AutoFixerPort {
        /** Apply a safe automated fix to a source file and return the patch diff. */
        String applyFix(String filePath, BreakingChange change) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum FixType { AUTO_FIXABLE, MANUAL_REQUIRED, DEPRECATION_WARNING }

    public record BreakingChange(
        String changeId,
        String fromSymbol,
        String toSymbol,
        String description,
        FixType fixType,
        RiskLevel riskLevel,
        String autoFixPattern   // regex pattern for scanner
    ) {}

    public record CodeUsage(
        String filePath,
        int lineNumber,
        String matchedText,
        String context
    ) {}

    public record MigrationIssue(
        BreakingChange change,
        CodeUsage usage,
        boolean fixApplied,
        String patchDiff
    ) {}

    public record MigrationReport(
        String fromVersion,
        String toVersion,
        String sourceDir,
        int totalIssues,
        int autoFixed,
        int manualRequired,
        List<MigrationIssue> issues,
        boolean hasHighRisk,
        String generatedAt
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final BreakingChangeRegistryPort breakingChangeRegistry;
    private final SourceCodeScannerPort scanner;
    private final AutoFixerPort autoFixer;
    private final Executor executor;
    private final Counter migrationsRunCounter;
    private final Counter highRiskCounter;

    public SdkMigrationToolingService(
        BreakingChangeRegistryPort breakingChangeRegistry,
        SourceCodeScannerPort scanner,
        AutoFixerPort autoFixer,
        MeterRegistry registry,
        Executor executor
    ) {
        this.breakingChangeRegistry = breakingChangeRegistry;
        this.scanner       = scanner;
        this.autoFixer     = autoFixer;
        this.executor      = executor;
        this.migrationsRunCounter = Counter.builder("sdk.migration.runs").register(registry);
        this.highRiskCounter      = Counter.builder("sdk.migration.high_risk_found").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Run the full migration analysis on a source directory.
     * Auto-applies fixes for AUTO_FIXABLE changes; leaves MANUAL_REQUIRED issues flagged.
     */
    public Promise<MigrationReport> migrate(
        String sourceDir,
        String fromVersion,
        String toVersion,
        boolean applyFixes
    ) {
        return Promise.ofBlocking(executor, () -> {
            List<BreakingChange> changes = breakingChangeRegistry.listChanges(fromVersion, toVersion);
            List<MigrationIssue> issues = new ArrayList<>();

            for (BreakingChange change : changes) {
                List<CodeUsage> usages = scanner.findUsages(sourceDir, change.autoFixPattern());
                for (CodeUsage usage : usages) {
                    String patch = null;
                    boolean fixed = false;
                    if (applyFixes && change.fixType() == FixType.AUTO_FIXABLE) {
                        try {
                            patch = autoFixer.applyFix(usage.filePath(), change);
                            fixed = true;
                        } catch (Exception e) {
                            patch = "// Auto-fix failed: " + e.getMessage();
                        }
                    }
                    issues.add(new MigrationIssue(change, usage, fixed, patch));
                }
            }

            int autoFixed = (int) issues.stream().filter(MigrationIssue::fixApplied).count();
            int manualRequired = (int) issues.stream()
                .filter(i -> !i.fixApplied() && i.change().fixType() == FixType.MANUAL_REQUIRED).count();
            boolean hasHighRisk = issues.stream()
                .anyMatch(i -> i.change().riskLevel() == RiskLevel.HIGH && !i.fixApplied());

            if (hasHighRisk) highRiskCounter.increment();
            migrationsRunCounter.increment();

            return new MigrationReport(
                fromVersion, toVersion, sourceDir,
                issues.size(), autoFixed, manualRequired, issues, hasHighRisk,
                java.time.Instant.now().toString()
            );
        });
    }

    /**
     * Dry-run: analyse without applying fixes. Useful for CI gate checks.
     */
    public Promise<MigrationReport> dryRun(String sourceDir, String fromVersion, String toVersion) {
        return migrate(sourceDir, fromVersion, toVersion, false);
    }

    /**
     * List all known breaking changes between two versions.
     */
    public Promise<List<BreakingChange>> listBreakingChanges(String fromVersion, String toVersion) {
        return Promise.ofBlocking(executor, () -> breakingChangeRegistry.listChanges(fromVersion, toVersion));
    }
}
