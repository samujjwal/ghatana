/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure Module
 */
package com.ghatana.yappc.infrastructure.datacloud.adapter;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a security scan operation.
 *
 * <p>Consumers should check {@link #isClean()} first. If not clean, iterate
 * {@link #findings()} for individual {@link Finding} details.
 *
 * @doc.type class
 * @doc.purpose Value object representing the result of a SecurityScanner scan
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public final class SecurityReport {

    /** Severity tiers for a security finding. */
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    /** Overall status of the scan result. */
    public enum Status { CLEAN, VULNERABLE, ERROR }

    /**
     * A single security finding within a scan result.
     *
     * @param ruleId    identifier of the rule or CVE that triggered the finding
     * @param message   human-readable description of the finding
     * @param severity  risk severity tier
     * @param location  file path and line context, may be empty if not applicable
     */
    public record Finding(String ruleId, String message, Severity severity, String location) {}

    private final Status status;
    private final List<Finding> findings;
    private final String scannerName;

    private SecurityReport(Status status, List<Finding> findings, String scannerName) {
        this.status      = status;
        this.findings    = Collections.unmodifiableList(findings);
        this.scannerName = scannerName;
    }

    /** Returns a clean report (no findings). */
    public static SecurityReport clean(String scannerName) {
        return new SecurityReport(Status.CLEAN, List.of(), scannerName);
    }

    /** Returns a report that contains one or more findings. */
    public static SecurityReport withFindings(List<Finding> findings, String scannerName) {
        if (findings.isEmpty()) {
            return clean(scannerName);
        }
        return new SecurityReport(Status.VULNERABLE, findings, scannerName);
    }

    /** Returns an error report when the scanner itself failed. */
    public static SecurityReport error(String reason, String scannerName) {
        Finding errorFinding = new Finding("SCAN_ERROR", reason, Severity.INFO, "");
        return new SecurityReport(Status.ERROR, List.of(errorFinding), scannerName);
    }

    /** {@code true} iff no security findings were detected. */
    public boolean isClean()            { return status == Status.CLEAN; }
    public Status getStatus()           { return status; }
    public List<Finding> findings()     { return findings; }
    public String getScannerName()      { return scannerName; }

    @Override
    public String toString() {
        return "SecurityReport{status=" + status +
               ", findings=" + findings.size() +
               ", scanner='" + scannerName + "'}";
    }
}
