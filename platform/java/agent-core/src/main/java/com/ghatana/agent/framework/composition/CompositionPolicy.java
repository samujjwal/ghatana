/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable policy governing how multiple agents are composed into a single
 * logical unit.
 *
 * @param compositionId   unique ID for this composition
 * @param pattern         structural composition pattern
 * @param memberAgentIds  agent IDs participating in the composition (immutable)
 * @param tenantId        tenant scope
 * @param votingPolicy    voting policy; required only for {@link CompositionPattern#VOTING}
 * @param aggregation     aggregation strategy; required for {@link CompositionPattern#SCATTER_GATHER}
 * @param timeoutMs       per-member call timeout in milliseconds; 0 = no timeout
 *
 * @doc.type class
 * @doc.purpose Immutable composition policy for multi-agent orchestration
 * @doc.layer platform
 * @doc.pattern Record
 */
public record CompositionPolicy(
        @NotNull String compositionId,
        @NotNull CompositionPattern pattern,
        @NotNull List<String> memberAgentIds,
        @NotNull String tenantId,
        @Nullable VotingPolicy votingPolicy,
        @Nullable AggregationStrategy aggregation,
        long timeoutMs
) {
    /** Compact constructor — validates required fields and makes collections immutable. */
    public CompositionPolicy {
        if (compositionId == null || compositionId.isBlank()) {
            throw new IllegalArgumentException("compositionId must not be blank");
        }
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(memberAgentIds, "memberAgentIds");
        if (memberAgentIds.isEmpty()) {
            throw new IllegalArgumentException("memberAgentIds must not be empty");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must be >= 0");
        }
        // Pattern-specific requirements
        if (pattern == CompositionPattern.VOTING && votingPolicy == null) {
            throw new IllegalArgumentException("votingPolicy is required for VOTING compositions");
        }
        if (pattern == CompositionPattern.SCATTER_GATHER && aggregation == null) {
            throw new IllegalArgumentException("aggregation is required for SCATTER_GATHER compositions");
        }
        memberAgentIds = List.copyOf(memberAgentIds);
    }
}
