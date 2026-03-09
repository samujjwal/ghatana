package com.ghatana.products.yappc.domain.enums;

/**
 * Enumeration of security scan types supported by the YAPPC platform.
 *
 * <p>This enum defines the different types of security scans that can be
 * performed on projects, each targeting different aspects of security.</p>
 *
 * @doc.type enum
 * @doc.purpose Defines the types of security scans available
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum ScanType {

    /**
     * Static Application Security Testing - analyzes source code.
     */
    SAST("Static Analysis", "Analyzes source code for security vulnerabilities"),

    /**
     * Dynamic Application Security Testing - tests running applications.
     */
    DAST("Dynamic Analysis", "Tests running applications for security issues"),

    /**
     * Software Composition Analysis - checks dependencies.
     */
    SCA("Dependency Scan", "Checks third-party dependencies for known vulnerabilities"),

    /**
     * Infrastructure as Code scanning.
     */
    IAC("IaC Scan", "Scans infrastructure as code for misconfigurations"),

    /**
     * Container image scanning.
     */
    CONTAINER("Container Scan", "Scans container images for vulnerabilities"),

    /**
     * Secret detection - scans for exposed secrets.
     */
    SECRET("Secret Detection", "Detects exposed secrets and credentials in code"),

    /**
     * Full comprehensive scan including all types.
     */
    FULL("Full Scan", "Comprehensive scan including all security checks"),

    /**
     * Quick scan - fast subset of checks.
     */
    QUICK("Quick Scan", "Fast scan with essential security checks only"),

    /**
     * Incremental scan - only changed files.
     */
    INCREMENTAL("Incremental Scan", "Scans only files changed since last scan");

    private final String displayName;
    private final String description;

    ScanType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a description of what this scan type does.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a comprehensive scan type.
     *
     * @return true if comprehensive
     */
    public boolean isComprehensive() {
        return this == FULL;
    }
}
