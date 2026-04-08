/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Memory governance contract for an agent release.
 *
 * <p>A {@code MemoryContract} specifies which memory classes the agent is allowed
 * to use, retention rules per class, provenance and redaction requirements, and
 * the shareability mode for any stored episodic or semantic memories.
 *
 * @param memoryContractId       unique identifier
 * @param version                semantic version string
 * @param allowedMemoryClasses   allowed memory class names (e.g., {@code "working"}, {@code "episodic"})
 * @param retentionRules         retention duration per memory class
 * @param provenanceRequirements set of provenance fields required for storage
 * @param redactionRequirements  set of fields that must be redacted before storage
 * @param shareabilityMode       one of {@code PRIVATE}, {@code TEAM}, {@code TENANT}, {@code PUBLIC}
 * @param digest                 SHA-256 of this contract's canonical representation
 * @param createdAt              when this memory contract was created
 *
 * @doc.type record
 * @doc.purpose Memory governance contract for an agent release
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record MemoryContract(
        String memoryContractId,
        String version,
        Set<String> allowedMemoryClasses,
        Map<String, Duration> retentionRules,
        Set<String> provenanceRequirements,
        Set<String> redactionRequirements,
        String shareabilityMode,
        String digest,
        Instant createdAt
) {
    public MemoryContract {
        allowedMemoryClasses    = Set.copyOf(allowedMemoryClasses);
        retentionRules          = Map.copyOf(retentionRules);
        provenanceRequirements  = Set.copyOf(provenanceRequirements);
        redactionRequirements   = Set.copyOf(redactionRequirements);
    }
}
