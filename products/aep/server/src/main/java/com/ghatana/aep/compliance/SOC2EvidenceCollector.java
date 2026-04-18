/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automated evidence collector for SOC 2 Type II compliance controls.
 * Collects real-time evidence from system sources for each control.
 *
 * @doc.type class
 * @doc.purpose Automated evidence collection for SOC 2 Type II controls
 * @doc.layer product
 * @doc.pattern Service
 */
public class SOC2EvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(SOC2EvidenceCollector.class);

    private final Map<String, List<EvidenceEntry>> evidenceStore = new ConcurrentHashMap<>();
    private final int maxEntriesPerControl = 1000;

    /**
     * Collects evidence for a specific control.
     *
     * @param controlId SOC 2 control ID (e.g., "CC6.1")
     * @param evidenceType type of evidence (e.g., "access_log", "configuration", "metric")
     * @param description human-readable description of the evidence
     * @param details evidence details as key-value pairs
     */
    public void collectEvidence(String controlId, String evidenceType, String description,
                                Map<String, Object> details) {
        EvidenceEntry entry = new EvidenceEntry(
            java.util.UUID.randomUUID().toString(),
            controlId,
            evidenceType,
            description,
            details != null ? Map.copyOf(details) : Map.of(),
            Instant.now()
        );

        evidenceStore.computeIfAbsent(controlId, k -> new ArrayList<>()).add(entry);
        
        // Prune old entries if over limit
        List<EvidenceEntry> entries = evidenceStore.get(controlId);
        if (entries.size() > maxEntriesPerControl) {
            entries.remove(0);
        }

        log.debug("[soc2-evidence] Collected evidence for control={} type={}: {}",
            controlId, evidenceType, description);
    }

    /**
     * Gets all evidence for a specific control.
     *
     * @param controlId SOC 2 control ID
     * @return list of evidence entries for the control
     */
    public List<EvidenceEntry> getEvidence(String controlId) {
        return List.copyOf(evidenceStore.getOrDefault(controlId, List.of()));
    }

    /**
     * Gets evidence for a control within a time range.
     *
     * @param controlId SOC 2 control ID
     * @param from start of time range
     * @param to end of time range
     * @return list of evidence entries within the time range
     */
    public List<EvidenceEntry> getEvidence(String controlId, Instant from, Instant to) {
        return evidenceStore.getOrDefault(controlId, List.of()).stream()
            .filter(entry -> !entry.timestamp().isBefore(from) && !entry.timestamp().isAfter(to))
            .toList();
    }

    /**
     * Gets evidence for all controls.
     *
     * @return map of control ID to evidence entries
     */
    public Map<String, List<EvidenceEntry>> getAllEvidence() {
        Map<String, List<EvidenceEntry>> copy = new HashMap<>();
        evidenceStore.forEach((id, entries) -> copy.put(id, List.copyOf(entries)));
        return copy;
    }

    /**
     * Generates an evidence summary report for a control.
     *
     * @param controlId SOC 2 control ID
     * @return summary of evidence for the control
     */
    public EvidenceSummary summarizeEvidence(String controlId) {
        List<EvidenceEntry> entries = evidenceStore.getOrDefault(controlId, List.of());
        
        if (entries.isEmpty()) {
            return new EvidenceSummary(controlId, 0, Map.of(), "No evidence collected");
        }

        Map<String, Long> evidenceByType = entries.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                EvidenceEntry::evidenceType,
                java.util.stream.Collectors.counting()
            ));

        Instant oldest = entries.stream()
            .map(EvidenceEntry::timestamp)
            .min(Instant::compareTo)
            .orElse(Instant.now());

        Instant newest = entries.stream()
            .map(EvidenceEntry::timestamp)
            .max(Instant::compareTo)
            .orElse(Instant.now());

        return new EvidenceSummary(
            controlId,
            entries.size(),
            evidenceByType,
            String.format("%d evidence entries collected between %s and %s",
                entries.size(), oldest, newest)
        );
    }

    /**
     * Clears all evidence for a control.
     *
     * @param controlId SOC 2 control ID
     */
    public void clearEvidence(String controlId) {
        evidenceStore.remove(controlId);
        log.info("[soc2-evidence] Cleared evidence for control={}", controlId);
    }

    /**
     * Clears all evidence.
     */
    public void clearAllEvidence() {
        evidenceStore.clear();
        log.info("[soc2-evidence] Cleared all evidence");
    }

    /**
     * Evidence entry record.
     *
     * @param id unique evidence entry identifier
     * @param controlId SOC 2 control ID
     * @param evidenceType type of evidence
     * @param description human-readable description
     * @param details evidence details
     * @param timestamp when the evidence was collected
     */
    public record EvidenceEntry(
        String id,
        String controlId,
        String evidenceType,
        String description,
        Map<String, Object> details,
        Instant timestamp
    ) {}

    /**
     * Evidence summary for a control.
     *
     * @param controlId SOC 2 control ID
     * @param totalEntries total number of evidence entries
     * @param evidenceByType count of entries by type
     * @param summary human-readable summary
     */
    public record EvidenceSummary(
        String controlId,
        int totalEntries,
        Map<String, Long> evidenceByType,
        String summary
    ) {}
}
