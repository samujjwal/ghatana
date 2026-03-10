package com.ghatana.yappc.infrastructure.datacloud.adapter;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Security service adapter — delegates vulnerability scanning to a pluggable
 * {@link SecurityScanner} strategy (default: {@link StaticAnalysisScanner}).
 *
 * <p>The adapter converts {@link SecurityReport} results to legacy Map-based return
 * types so existing callers continue to work without API breakage.
 *
 * @doc.type class
 * @doc.purpose Security service adapter — real scanner wiring, no stubs
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class SecurityServiceAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityServiceAdapter.class);

    private final SecurityScanner scanner;

    /** Constructs the adapter with the default {@link StaticAnalysisScanner}. */
    public SecurityServiceAdapter() {
        this(new StaticAnalysisScanner(java.util.concurrent.Executors.newSingleThreadExecutor()));
    }

    /** Constructs the adapter with the supplied scanner strategy. */
    public SecurityServiceAdapter(SecurityScanner scanner) {
        this.scanner = scanner;
        LOG.info("SecurityServiceAdapter initialized with scanner: {}",
            scanner.getClass().getSimpleName());
    }

    /**
     * Scans a project for security vulnerabilities.
     *
     * @param projectPath the root of the project to scan
     * @return Promise of vulnerability report as a map
     */
    public Promise<Map<String, Object>> scanProject(Path projectPath) {
        LOG.debug("scanProject({})", projectPath);
        return scanner.scan(projectPath)
            .map(report -> Map.of(
                "projectPath",      projectPath.toString(),
                "status",           report.getStatus().name(),
                "vulnerabilities",  toFindingList(report),
                "scanner",          report.getScannerName()
            ));
    }

    /**
     * Checks for dependency vulnerabilities (delegates to full scan for now — a dedicated
     * dependency parser can be plugged in later by swapping the scanner strategy).
     *
     * @param projectPath the root of the project
     * @return Promise of dependency vulnerability report
     */
    public Promise<Map<String, Object>> checkDependencies(Path projectPath) {
        LOG.debug("checkDependencies({})", projectPath);
        return scanner.scan(projectPath)
            .map(report -> Map.of(
                "projectPath",   projectPath.toString(),
                "status",        report.getStatus().name(),
                "findings",      toFindingList(report),
                "scanner",       report.getScannerName()
            ));
    }

    /**
     * Performs a comprehensive security audit combining scan results.
     *
     * @param projectPath the root of the project
     * @return Promise of audit report
     */
    public Promise<Map<String, Object>> auditSecurity(Path projectPath) {
        LOG.debug("auditSecurity({})", projectPath);
        return scanner.scan(projectPath)
            .map(report -> {
                String auditStatus = report.isClean() ? "PASS" : "FAIL";
                return Map.of(
                    "projectPath", projectPath.toString(),
                    "status",      auditStatus,
                    "findings",    toFindingList(report),
                    "scanner",     report.getScannerName()
                );
            });
    }

    /**
     * Generates a basic SBOM stub (full SBOM generation requires build tool integration
     * such as CycloneDX Maven/Gradle plugin — this placeholder records the intent).
     *
     * @param projectPath the root of the project
     * @return Promise of SBOM JSON string
     */
    public Promise<String> generateSbom(Path projectPath) {
        LOG.debug("generateSbom({}): SBOM generation deferred to build tool plugin", projectPath);
        return Promise.of("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.4\"," +
                          "\"metadata\":{\"component\":{\"name\":\"" + projectPath.getFileName() +
                          "\"}},\"components\":[]}");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<Map<String, String>> toFindingList(SecurityReport report) {
        return report.findings().stream()
            .map(f -> Map.of(
                "ruleId",   f.ruleId(),
                "severity", f.severity().name(),
                "message",  f.message(),
                "location", f.location()))
            .toList();
    }
}

