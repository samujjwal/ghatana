package com.ghatana.platform.plugin;

import java.time.Instant;

/**
 * Records a complete plugin interaction cycle from request to evidence persistence.
 *
 * <p>Cycle tracking enables audit trails, debugging, and performance analysis for
 * plugin interactions. Each cycle includes the full request/response lifecycle with
 * timestamps and policy decisions.</p>
 *
 * @doc.type record
 * @doc.purpose Track complete plugin interaction cycles for audit and observability
 * @doc.layer kernel-plugin
 * @doc.pattern AuditRecord
 */
public record PluginCycleRecord(
    String cycleId,
    String contractId,
    String callerPluginId,
    String targetPluginId,
    String tenantId,
    String workspaceId,
    String lifecyclePhase,
    Instant requestedAt,
    Instant policyEvaluatedAt,
    Instant dispatchedAt,
    Instant completedAt,
    Instant evidencePersistedAt,
    String policyDecision,
    String reasonCode,
    String status,
    long durationMs
) {
    public static PluginCycleRecord create(String cycleId, String contractId, PluginInteractionEnvelope<?> envelope) {
        return new PluginCycleRecord(
            cycleId,
            contractId,
            envelope.callerPluginId(),
            envelope.targetPluginId(),
            envelope.tenantId(),
            envelope.workspaceId(),
            envelope.lifecyclePhase(),
            envelope.requestedAt(),
            null,  // policyEvaluatedAt
            null,  // dispatchedAt
            null,  // completedAt
            null,  // evidencePersistedAt
            null,  // policyDecision
            null,  // reasonCode
            "started",
            0L
        );
    }
}
