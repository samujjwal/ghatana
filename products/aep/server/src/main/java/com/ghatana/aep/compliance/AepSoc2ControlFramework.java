/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SOC 2 control framework for AEP compliance reporting.
 *
 * @doc.type class
 * @doc.purpose SOC 2 compliance control framework and report generation
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepSoc2ControlFramework {

    private final SOC2EvidenceCollector evidenceCollector;

    /**
     * Creates a SOC 2 control framework with automated evidence collection.
     */
    public AepSoc2ControlFramework() {
        this.evidenceCollector = new SOC2EvidenceCollector();
    }

    /**
     * Creates a SOC 2 control framework with a custom evidence collector.
     *
     * @param evidenceCollector custom evidence collector
     */
    public AepSoc2ControlFramework(SOC2EvidenceCollector evidenceCollector) {
        this.evidenceCollector = evidenceCollector;
    }

    /**
     * Gets the evidence collector for this framework.
     *
     * @return the evidence collector
     */
    public SOC2EvidenceCollector getEvidenceCollector() {
        return evidenceCollector;
    }

    /**
     * Generates a SOC 2 compliance report for the AEP platform.
     *
     * @return the SOC 2 report
     */
    public Soc2Report generateReport() {
        // Generate control status based on collected evidence
        List<ControlStatus> controls = new ArrayList<>();
        
        for (String controlId : List.of("CC6.1", "CC6.2", "CC6.3", "CC7.1", "CC7.2", "CC8.1")) {
            SOC2EvidenceCollector.EvidenceSummary summary = evidenceCollector.summarizeEvidence(controlId);
            String status = summary.totalEntries() > 0 ? "PASS" : "WARNING";
            String description = getControlDescription(controlId);
            controls.add(new ControlStatus(controlId, description, status));
        }

        return new Soc2Report(
                "AEP SOC 2 Type II Report",
                Instant.now().toString(),
                determineOverallStatus(controls),
                controls,
                Map.of(
                        "platform", "AEP",
                        "framework", "SOC 2 Type II",
                        "scope", "Trust Services Criteria",
                        "evidenceCollected", String.valueOf(evidenceCollector.getAllEvidence().values().stream().mapToInt(List::size).sum())
                )
        );
    }

    private String determineOverallStatus(List<ControlStatus> controls) {
        boolean allPass = controls.stream().allMatch(c -> c.status().equals("PASS"));
        boolean anyWarning = controls.stream().anyMatch(c -> c.status().equals("WARNING"));
        
        if (allPass) {
            return "COMPLIANT";
        } else if (anyWarning) {
            return "WARNING";
        } else {
            return "NON_COMPLIANT";
        }
    }

    private String getControlDescription(String controlId) {
        return switch (controlId) {
            case "CC6.1" -> "Logical and Physical Access Controls";
            case "CC6.2" -> "System Access Management";
            case "CC6.3" -> "Role-Based Access";
            case "CC7.1" -> "System Monitoring";
            case "CC7.2" -> "Incident Response";
            case "CC8.1" -> "Change Management";
            default -> "Unknown Control";
        };
    }

    /**
     * SOC 2 compliance report.
     */
    public record Soc2Report(
            String title,
            String generatedAt,
            String overallStatus,
            List<ControlStatus> controls,
            Map<String, String> metadata
    ) {}

    /**
     * Individual control status within the SOC 2 report.
     */
    public record ControlStatus(
            String controlId,
            String description,
            String status
    ) {}
}
