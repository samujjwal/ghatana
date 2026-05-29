/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Typed context for phase action authorization decisions.
 *
 * <p>Encapsulates all runtime metadata needed to determine which actions
 * are available for a phase, including run status, evidence, risk assessment,
 * and rollback/promote targets.
 *
 * @doc.type class
 * @doc.purpose Encapsulate run action context for authorization decisions
 * @doc.layer services
 * @doc.pattern Value Object
 */
public final class RunActionContext {

    private final List<String> evidenceIds;
    private final String supportTrace;
    private final String riskReason;
    private final String targetVersion;
    private final String targetEnvironment;
    private final String latestRunId;
    private final boolean rollbackSupported;

    private RunActionContext(
            @NotNull List<String> evidenceIds,
            @NotNull String supportTrace,
            @NotNull String riskReason,
            @NotNull String targetVersion,
            @NotNull String targetEnvironment,
            @NotNull String latestRunId,
            boolean rollbackSupported
    ) {
        this.evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        this.supportTrace = Objects.requireNonNull(supportTrace, "supportTrace");
        this.riskReason = Objects.requireNonNull(riskReason, "riskReason");
        this.targetVersion = Objects.requireNonNull(targetVersion, "targetVersion");
        this.targetEnvironment = Objects.requireNonNull(targetEnvironment, "targetEnvironment");
        this.latestRunId = Objects.requireNonNull(latestRunId, "latestRunId");
        this.rollbackSupported = rollbackSupported;
    }

    /**
     * Creates a context from platform run status.
     *
     * @param platformRunStatus optional platform run status
     * @param evidenceIds evidence IDs from phase packet
     * @param blockers list of phase blockers
     * @return populated context
     */
    public static RunActionContext fromPlatformRunStatus(
            @Nullable com.ghatana.yappc.api.PhasePacket.PlatformRunStatus platformRunStatus,
            @NotNull List<String> evidenceIds,
            @NotNull List<com.ghatana.yappc.api.PhasePacket.PhaseBlocker> blockers
    ) {
        String riskReason = blockers.isEmpty() ? "low" : "high";

        if (platformRunStatus == null) {
            return degraded(evidenceIds, blockers);
        }

        return new RunActionContext(
                evidenceIds,
                valueOrEmpty(platformRunStatus.traceId()),
                valueOrDefault(platformRunStatus.riskLevel(), riskReason),
                valueOrEmpty(platformRunStatus.rollbackTarget()),
                valueOrEmpty(platformRunStatus.promoteTarget()),
                valueOrEmpty(platformRunStatus.runId()),
                platformRunStatus.rollbackSupported()
        );
    }

    /**
     * Creates a degraded context when platform run status is unavailable.
     *
     * @param evidenceIds evidence IDs from phase packet
     * @param blockers list of phase blockers
     * @return degraded context with empty targets
     */
    public static RunActionContext degraded(
            @NotNull List<String> evidenceIds,
            @NotNull List<com.ghatana.yappc.api.PhasePacket.PhaseBlocker> blockers
    ) {
        String riskReason = blockers.isEmpty() ? "low" : "high";
        return new RunActionContext(
                evidenceIds,
                "",
                riskReason,
                "",
                "",
                "",
                false
        );
    }

    @NotNull
    public List<String> evidenceIds() {
        return evidenceIds;
    }

    @NotNull
    public String supportTrace() {
        return supportTrace;
    }

    @NotNull
    public String riskReason() {
        return riskReason;
    }

    @NotNull
    public String targetVersion() {
        return targetVersion;
    }

    @NotNull
    public String targetEnvironment() {
        return targetEnvironment;
    }

    @NotNull
    public String latestRunId() {
        return latestRunId;
    }

    public boolean rollbackSupported() {
        return rollbackSupported;
    }

    @NotNull
    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NotNull
    private static String valueOrDefault(@Nullable String value, @NotNull String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
