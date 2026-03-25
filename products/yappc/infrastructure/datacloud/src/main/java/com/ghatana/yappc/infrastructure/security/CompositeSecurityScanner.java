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
 * Composite security scanner that aggregates results from multiple {@link SecurityScanner}
 * delegates (e.g., SAST + OSV/dependency scanning).
 *
 * <p>All delegates are run against the project path and their findings merged into a
 * single {@link SecurityReport}. The composite is named after its delegates.
 *
 * @doc.type class
 * @doc.purpose Composite security scanner aggregating results from multiple scanners
 * @doc.layer infrastructure
 * @doc.pattern Composite
 */
public final class CompositeSecurityScanner implements SecurityScanner {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeSecurityScanner.class);

    private final List<SecurityScanner> delegates;
    private final String scannerName;

    /**
     * Creates a composite from the supplied scanner list.
     *
     * @param delegates one or more scanners to run in sequence; must not be null or empty
     */
    public CompositeSecurityScanner(List<SecurityScanner> delegates) {
        Objects.requireNonNull(delegates, "delegates required");
        if (delegates.isEmpty()) throw new IllegalArgumentException("At least one scanner required");
        this.delegates   = List.copyOf(delegates);
        this.scannerName = buildName(delegates);
    }

    @Override
    public Promise<SecurityReport> scan(Path projectPath) {
        List<Promise<SecurityReport>> promises = delegates.stream()
            .map(d -> d.scan(projectPath))
            .toList();

        return Promises.toList(promises)
            .map(results -> {
                List<SecurityReport.Finding> allFindings = new ArrayList<>();
                for (SecurityReport r : results) {
                    allFindings.addAll(r.findings());
                }
                LOG.debug("[composite-scanner] {} delegate(s), {} total finding(s) for {}",
                    delegates.size(), allFindings.size(), projectPath);
                return allFindings.isEmpty()
                    ? SecurityReport.clean(scannerName)
                    : SecurityReport.withFindings(allFindings, scannerName);
            })
            .then(
                result -> Promise.of(result),
                e -> {
                    LOG.warn("[composite-scanner] One or more delegates failed for {}: {}",
                        projectPath, e.getMessage());
                    return Promise.of(SecurityReport.error(
                        "Composite scan failed: " + e.getMessage(), scannerName));
                }
            );
    }

    private static String buildName(List<SecurityScanner> scanners) {
        StringBuilder sb = new StringBuilder("composite[");
        for (int i = 0; i < scanners.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(scanners.get(i).getClass().getSimpleName());
        }
        return sb.append(']').toString();
    }
}
