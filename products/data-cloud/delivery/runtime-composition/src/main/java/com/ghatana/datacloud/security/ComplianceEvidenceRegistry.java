/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Phase 3 — Security Hardening: Compliance evidence registry tracking implementation
 * status and audit evidence for SOC2, ISO 27001, HIPAA, and PCI DSS controls.
 */
package com.ghatana.datacloud.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry tracking the implementation status and audit evidence for each compliance control
 * across supported frameworks: SOC2, ISO 27001, HIPAA, and PCI DSS.
 *
 * <p>This registry separates documented controls from certified compliance — registering
 * a control as {@link ControlStatus#DOCUMENTED} means it has been described, but not yet
 * verified. Only controls at {@link ControlStatus#CERTIFIED} satisfy the requirement for
 * a framework to be considered compliant.
 *
 * <p>Usage example:
 * <pre>{@code
 * ComplianceEvidenceRegistry registry = new ComplianceEvidenceRegistry();
 * registry.register(new ComplianceControl(
 *     "SOC2-CC6.1", ComplianceFramework.SOC2, "Logical Access Controls",
 *     ControlStatus.IMPLEMENTED, "RBAC enforced via DataCloudSecurityFilter",
 *     List.of("https://internal/docs/rbac"), Instant.now(), "security-team"
 * ));
 * boolean compliant = registry.isFrameworkCompliant(ComplianceFramework.SOC2);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Track compliance control implementation status and evidence for SOC2, ISO 27001, HIPAA, and PCI DSS
 * @doc.layer security
 * @doc.pattern Registry
 */
public class ComplianceEvidenceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceEvidenceRegistry.class);

    /**
     * Supported compliance frameworks.
     */
    public enum ComplianceFramework {
        SOC2,
        ISO_27001,
        HIPAA,
        PCI_DSS
    }

    /**
     * Lifecycle status of a compliance control.
     *
     * <p>The progression is: DOCUMENTED → IMPLEMENTED → TESTED → CERTIFIED.
     * {@link #NOT_APPLICABLE} is used for controls the workload is explicitly exempt from.
     */
    public enum ControlStatus {
        /** Control has been described but not yet implemented. */
        DOCUMENTED,
        /** Control is implemented but not yet independently tested. */
        IMPLEMENTED,
        /** Control has passed internal validation tests. */
        TESTED,
        /** Control has been independently verified (e.g., by an auditor). */
        CERTIFIED,
        /** Control does not apply to this workload. */
        NOT_APPLICABLE
    }

    /**
     * An immutable record of a single compliance control with its evidence.
     */
    public static final class ComplianceControl {
        private final String controlId;
        private final ComplianceFramework framework;
        private final String requirement;
        private final ControlStatus status;
        private final String evidenceDescription;
        private final List<String> evidenceLinks;
        private final Instant lastVerified;
        private final String owner;

        public ComplianceControl(String controlId,
                                 ComplianceFramework framework,
                                 String requirement,
                                 ControlStatus status,
                                 String evidenceDescription,
                                 List<String> evidenceLinks,
                                 Instant lastVerified,
                                 String owner) {
            Objects.requireNonNull(controlId, "controlId must not be null");
            Objects.requireNonNull(framework, "framework must not be null");
            Objects.requireNonNull(requirement, "requirement must not be null");
            Objects.requireNonNull(status, "status must not be null");
            this.controlId = controlId;
            this.framework = framework;
            this.requirement = requirement;
            this.status = status;
            this.evidenceDescription = evidenceDescription != null ? evidenceDescription : "";
            this.evidenceLinks = evidenceLinks != null ? List.copyOf(evidenceLinks) : List.of();
            this.lastVerified = lastVerified != null ? lastVerified : Instant.now();
            this.owner = owner != null ? owner : "";
        }

        public String getControlId() { return controlId; }
        public ComplianceFramework getFramework() { return framework; }
        public String getRequirement() { return requirement; }
        public ControlStatus getStatus() { return status; }
        public String getEvidenceDescription() { return evidenceDescription; }
        public List<String> getEvidenceLinks() { return evidenceLinks; }
        public Instant getLastVerified() { return lastVerified; }
        public String getOwner() { return owner; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ComplianceControl other)) return false;
            return controlId.equals(other.controlId);
        }

        @Override
        public int hashCode() { return controlId.hashCode(); }

        @Override
        public String toString() {
            return String.format("ComplianceControl{id='%s', framework=%s, status=%s}",
                controlId, framework, status);
        }
    }

    /**
     * Summary statistics for a single compliance framework.
     */
    public static final class FrameworkSummary {
        private final ComplianceFramework framework;
        private final Map<ControlStatus, Long> countsByStatus;
        private final long totalControls;
        private final boolean compliant;

        public FrameworkSummary(ComplianceFramework framework,
                                Map<ControlStatus, Long> countsByStatus,
                                boolean compliant) {
            this.framework = framework;
            this.countsByStatus = Map.copyOf(countsByStatus);
            this.totalControls = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
            this.compliant = compliant;
        }

        public ComplianceFramework getFramework() { return framework; }
        public Map<ControlStatus, Long> getCountsByStatus() { return countsByStatus; }
        public long getTotalControls() { return totalControls; }
        public long getCertifiedCount() { return countsByStatus.getOrDefault(ControlStatus.CERTIFIED, 0L); }
        public boolean isCompliant() { return compliant; }

        @Override
        public String toString() {
            return String.format("FrameworkSummary{framework=%s, total=%d, certified=%d, compliant=%s}",
                framework, totalControls, getCertifiedCount(), compliant);
        }
    }

    /**
     * A cross-framework compliance report.
     */
    public static final class ComplianceReport {
        private final Instant generatedAt;
        private final Map<ComplianceFramework, FrameworkSummary> frameworkSummaries;

        public ComplianceReport(Map<ComplianceFramework, FrameworkSummary> frameworkSummaries) {
            this.generatedAt = Instant.now();
            this.frameworkSummaries = Map.copyOf(frameworkSummaries);
        }

        public Instant getGeneratedAt() { return generatedAt; }
        public Map<ComplianceFramework, FrameworkSummary> getFrameworkSummaries() { return frameworkSummaries; }

        public boolean isFullyCompliant() {
            return frameworkSummaries.values().stream().allMatch(FrameworkSummary::isCompliant);
        }

        @Override
        public String toString() {
            return String.format("ComplianceReport{generatedAt=%s, fullyCompliant=%s, frameworks=%s}",
                generatedAt, isFullyCompliant(), frameworkSummaries.keySet());
        }
    }

    // -------------------------------------------------------------------------
    // Registry State
    // -------------------------------------------------------------------------

    private final Map<String, ComplianceControl> controls = new ConcurrentHashMap<>();

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Register or update a compliance control.
     *
     * <p>If a control with the same {@code controlId} already exists it is replaced.
     *
     * @param control the control to register; must not be null
     */
    public void register(ComplianceControl control) {
        Objects.requireNonNull(control, "control must not be null");
        controls.put(control.getControlId(), control);
        logger.info("Registered compliance control: {} [{}] status={}", control.getControlId(),
            control.getFramework(), control.getStatus());
    }

    /**
     * Retrieve a specific control by its ID.
     *
     * @param controlId the control identifier
     * @return an {@link Optional} containing the control if found
     */
    public Optional<ComplianceControl> getControl(String controlId) {
        return Optional.ofNullable(controls.get(controlId));
    }

    /**
     * Retrieve all controls registered for a given framework.
     *
     * @param framework the target framework
     * @return unmodifiable list of controls for the framework (may be empty)
     */
    public List<ComplianceControl> getControlsByFramework(ComplianceFramework framework) {
        Objects.requireNonNull(framework, "framework must not be null");
        return controls.values().stream()
            .filter(c -> c.getFramework() == framework)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Retrieve all controls in a given lifecycle status.
     *
     * @param status the target status
     * @return unmodifiable list of controls with that status (may be empty)
     */
    public List<ComplianceControl> getControlsByStatus(ControlStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return controls.values().stream()
            .filter(c -> c.getStatus() == status)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Count controls per status for a given framework.
     *
     * @param framework the target framework
     * @return map from {@link ControlStatus} to count
     */
    public Map<ControlStatus, Long> getStatusCountsByFramework(ComplianceFramework framework) {
        return getControlsByFramework(framework).stream()
            .collect(Collectors.groupingBy(ComplianceControl::getStatus, Collectors.counting()));
    }

    /**
     * Determine whether all registered controls for a framework are either
     * {@link ControlStatus#CERTIFIED} or {@link ControlStatus#NOT_APPLICABLE}.
     *
     * <p>An empty framework (no controls registered) is considered <em>non-compliant</em>
     * because there is no evidence to rely on.
     *
     * @param framework the framework to evaluate
     * @return {@code true} only when every control is CERTIFIED or NOT_APPLICABLE
     */
    public boolean isFrameworkCompliant(ComplianceFramework framework) {
        List<ComplianceControl> frameworkControls = getControlsByFramework(framework);
        if (frameworkControls.isEmpty()) {
            return false;
        }
        return frameworkControls.stream()
            .allMatch(c -> c.getStatus() == ControlStatus.CERTIFIED
                || c.getStatus() == ControlStatus.NOT_APPLICABLE);
    }

    /**
     * Generate a point-in-time compliance report across all registered frameworks.
     *
     * @return {@link ComplianceReport} containing per-framework summaries
     */
    public ComplianceReport generateReport() {
        Map<ComplianceFramework, FrameworkSummary> summaries = new EnumMap<>(ComplianceFramework.class);

        for (ComplianceFramework framework : ComplianceFramework.values()) {
            Map<ControlStatus, Long> counts = getStatusCountsByFramework(framework);
            boolean compliant = isFrameworkCompliant(framework);
            summaries.put(framework, new FrameworkSummary(framework, counts, compliant));
        }

        ComplianceReport report = new ComplianceReport(summaries);
        logger.info("Generated compliance report: fullyCompliant={}", report.isFullyCompliant());
        return report;
    }

    /**
     * Return the total number of registered controls.
     *
     * @return control count
     */
    public int size() {
        return controls.size();
    }
}
