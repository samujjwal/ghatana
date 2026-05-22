package com.ghatana.platform.plugin;

import java.time.Instant;
import java.util.Map;

/**
 * Typed contract for plugin interaction evidence records.
 *
 * <p>Defines the canonical structure for plugin interaction evidence that is
 * persisted to storage (file system, Data Cloud, or other providers). This contract
 * ensures type safety and consistency across all evidence writers.</p>
 *
 * @doc.type record
 * @doc.purpose Canonical structure for plugin interaction evidence records
 * @doc.layer kernel-plugin
 * @doc.pattern Contract
 */
public record PluginInteractionEvidenceContract(
    String schemaVersion,
    String evidenceId,
    String manifestType,
    String contractId,
    String contractVersion,
    String callerPluginId,
    String targetPluginId,
    String tenantId,
    String workspaceId,
    String lifecyclePhase,
    String cycleId,
    String correlationId,
    Instant requestedAt,
    Instant completedAt,
    Instant capturedAt,
    String status,
    String policyDecision,
    String reasonCode,
    Map<String, Object> payload,
    Map<String, Object> result,
    Map<String, String> metadata
) {
    public static PluginInteractionEvidenceContract fromCycleRecord(
        PluginCycleRecord cycle,
        Map<String, Object> payload,
        Map<String, Object> result,
        Map<String, String> metadata
    ) {
        return new PluginInteractionEvidenceContract(
            "1.0.0",
            cycle.cycleId(),
            "plugin-interaction-evidence",
            cycle.contractId(),
            "1.0.0",
            cycle.callerPluginId(),
            cycle.targetPluginId(),
            cycle.tenantId(),
            cycle.workspaceId(),
            cycle.lifecyclePhase(),
            cycle.cycleId(),
            cycle.cycleId(),
            cycle.requestedAt(),
            cycle.completedAt(),
            Instant.now(),
            cycle.status(),
            cycle.policyDecision(),
            cycle.reasonCode(),
            payload,
            result,
            metadata
        );
    }
}
