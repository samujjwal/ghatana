/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.lifecycle.AgentTurnTrace;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Converts turn outcomes into bounded learning candidates
 * @doc.layer agent-core
 * @doc.pattern Service
 */
/**
 * Converts turn outcomes into bounded learning candidates.
 */
public final class LearningReflectionService {

    public @NotNull LearningCandidate propose(
            @NotNull LearningContract contract,
            @NotNull AgentResult<?> result,
            @NotNull String agentId,
            @NotNull LearningTarget target,
            @NotNull Map<String, Object> proposedArtifact) {
        LearningPermissionChecker.requireAllowed(contract, target);
        String traceId = result.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required for learned artifact provenance");
        }
        Map<String, Object> payload = new LinkedHashMap<>(proposedArtifact);
        payload.putIfAbsent("sourceStatus", result.getStatus().name());
        payload.putIfAbsent("sourceConfidence", result.getConfidence());
        if (!result.getWarnings().isEmpty()) {
            payload.putIfAbsent("warnings", result.getWarnings());
        }
        return new LearningCandidate(
                "lc-" + UUID.randomUUID(),
                agentId,
                result.getAgentReleaseId(),
                traceId,
                target,
                LearningCandidateState.PROPOSED,
                result.getMemoryRefs().isEmpty() ? java.util.List.of(traceId) : result.getMemoryRefs(),
                payload,
                Instant.now());
    }

    public @NotNull LearningCandidate proposeFromTrace(
            @NotNull LearningContract contract,
            @NotNull AgentTurnTrace trace,
            @NotNull LearningTarget target,
            @NotNull Map<String, Object> proposedArtifact) {
        LearningPermissionChecker.requireAllowed(contract, target);
        Map<String, Object> payload = new LinkedHashMap<>(proposedArtifact);
        payload.putIfAbsent("sourceStatus", trace.status());
        payload.putIfAbsent("phaseTraceRefs", trace.phases().stream()
                .map(com.ghatana.agent.lifecycle.AgentPhaseTrace::phaseTraceId)
                .toList());
        return new LearningCandidate(
                "lc-" + UUID.randomUUID(),
                trace.agentId(),
                null,
                trace.traceId(),
                target,
                LearningCandidateState.PROPOSED,
                java.util.List.of(trace.traceId()),
                payload,
                Instant.now());
    }
}
