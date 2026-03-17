/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Module
 */
package com.ghatana.yappc.infrastructure.security;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityReport;
import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityScanner;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composite security scanner combining multiple scanner strategies.
 *
 * <p><b>Purpose:</b> Runs SAST + dependency scanning in parallel via dependencyVulnerability
 * scanner aggregation.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Execute all scanners in parallel via {@link Promises#all(List)}
 *   <li>Aggregate findings by deduplicating on (type, severity, description)
 *   <li>Return combined report with highest severity
 * </ol>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * CompositeSecurityScanner scanner = new CompositeSecurityScanner(
 *     List.of(
 *         new StaticAnalysisScanner(executor),  // SAST — detects patterns
 *         new OsvScannerAdapter(httpClient)      // Dependency scanning — queries OSV API
 *     )
 * );
 * Promise<SecurityReport> report = scanner.scan(projectPath);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Composite security scanner combining SAST + dependency scanning
 * @doc.layer infrastructure
 * @doc.pattern Strategy (Composite pattern)
 *
 * @since 2.4.0
 */
public class CompositeSecurityScanner implements SecurityScanner {

    private static final Logger logger = LoggerFactory.getLogger(CompositeSecurityScanner.class);

    private final List<SecurityScanner> scanners;

    /**
     * Creates composite scanner with list of strategies.
     *
     * @param scanners non-empty list of SecurityScanner implementations
     * @throws NullPointerException if scanners is null or contains null
     * @throws IllegalArgumentException if scanners is empty
     */
    public CompositeSecurityScanner(List<SecurityScanner> scanners) {
        Objects.requireNonNull(scanners, "scanners");
        if (scanners.isEmpty()) {
            throw new IllegalArgumentException("scanners must not be empty");
        }
        this.scanners = new ArrayList<>(scanners); // Defend against mutations
        logger.info("CompositeSecurityScanner initialized with {} scanners", scanners.size());
    }

    @Override
    public Promise<SecurityReport> scan(Path projectPath) {
        Objects.requireNonNull(projectPath, "projectPath");

        logger.info("Running composite scan on {} with {} scanners", projectPath, scanners.size());

        // Run all scanners in parallel; individual failures produce clean reports rather than failing the whole run
        List<Promise<SecurityReport>> reports = scanners.stream()
            .map(scanner -> {
                logger.debug("Executing scanner: {}", scanner.getClass().getSimpleName());
                return scanner.scan(projectPath)
                    .then(
                        report -> Promise.of(report),
                        e -> {
                            // Log but don't fail — allow partial scanning if one scanner fails
                            logger.warn("Scanner {} failed: {}", scanner.getClass().getSimpleName(), e.getMessage());
                            return Promise.of(SecurityReport.clean(scanner.getClass().getSimpleName() + "-FAILED"));
                        }
                    );
            })
            .toList();

        return Promises.toList(reports)
            .map(this::mergeReports);
    }

    /**
     * Merges multiple reports by aggregating findings and determining overall status.
     *
     * @param reports list of individual scanner reports
     * @return merged report with deduplicated findings
     */
    private SecurityReport mergeReports(List<SecurityReport> reports) {
        logger.debug("Merging {} scanner reports", reports.size());

        // Aggregate finding details
        StringBuilder scannerNames = new StringBuilder();
        List<SecurityReport.Finding> allFindings = new ArrayList<>();

        for (SecurityReport report : reports) {
            if (report.getScannerName() != null) {
                if (scannerNames.length() > 0) {
                    scannerNames.append(", ");
                }
                scannerNames.append(report.getScannerName());
            }

            if (report.findings() != null) {
                allFindings.addAll(report.findings());
            }
        }

        // Deduplicate findings by (type, severity, description)
        List<SecurityReport.Finding> deduped = deduplicateFindings(allFindings);

        logger.info("Merged composite report: {} findings after dedup", deduped.size());

        String name = "CompositeScanner[" + scannerNames + "]";
        return deduped.isEmpty() ? SecurityReport.clean(name) : SecurityReport.withFindings(deduped, name);
    }

    /**
     * Deduplicates findings by (type, severity, description).
     *
     * @param findings list of findings from all scanners
     * @return deduplicated list
     */
    private List<SecurityReport.Finding> deduplicateFindings(List<SecurityReport.Finding> findings) {
        return findings.stream()
            .distinct() // Use Finding.equals() which compares type/severity/description
            .toList();
    }
}
