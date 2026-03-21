/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Rich metadata record describing an agent's identity, type, SLAs,
 * computational guarantees, and declared capabilities.
 *
 * <p>The descriptor is immutable and completely characterises an agent
 * for scheduling, routing, discovery, and capacity planning — without
 * requiring access to the running agent instance.
 *
 * @doc.type record
 * @doc.purpose Agent identity and capability metadata
 * @doc.layer core
 * @doc.pattern ValueObject
 */
@Value
@Builder(toBuilder = true)
public class AgentDescriptor {

    // Identity
    /** Globally unique agent ID. */
    String agentId;

    /** Human-readable name for display and documentation. */
    String name;

    /** Semantic version string (e.g., "2.1.0"). */
    @Builder.Default
    String version = "1.0.0";

    /** Description of what the agent does. */
    String description;

    /** Namespace for multi-tenant or organizational grouping. */
    @Builder.Default
    String namespace = "default";

    // Type classification
    /** Primary agent type from the unified taxonomy. */
    AgentType type;

    /** Optional subtype for finer classification (e.g., "RULE_BASED", "LLM"). */
    String subtype;

    /** Determinism guarantee this agent provides. */
    @Builder.Default
    DeterminismGuarantee determinism = DeterminismGuarantee.NONE;

    // SLAs
    /** Maximum acceptable processing latency for a single input. */
    @Builder.Default
    Duration latencySla = Duration.ofSeconds(5);

    /** Target throughput in events per second. */
    @Builder.Default
    long throughputTarget = 1000;

    // Operational characteristics
    /** How this agent manages mutable state. */
    @Builder.Default
    StateMutability stateMutability = StateMutability.STATELESS;

    /** How this agent behaves on processing failure. */
    @Builder.Default
    FailureMode failureMode = FailureMode.FAIL_FAST;

    // Capabilities
    /** Capability tags for discovery and routing. */
    @Builder.Default
    Set<String> capabilities = Set.of();

    /** Event types this agent can consume as input. Empty means any. */
    @Builder.Default
    Set<String> inputEventTypes = Set.of();

    /** Event types this agent produces as output. */
    @Builder.Default
    Set<String> outputEventTypes = Set.of();

    /** Arbitrary metadata for extensions. */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    // Labels & Annotations
    /** Key-value labels for filtering and selection. */
    @Builder.Default
    Map<String, String> labels = Map.of();

    /** Key-value annotations for non-identifying metadata. */
    @Builder.Default
    Map<String, String> annotations = Map.of();

    /** Whether this agent is deterministic. */
    public boolean isDeterministic() {
        return determinism == DeterminismGuarantee.FULL
                || determinism == DeterminismGuarantee.CONFIG_SCOPED;
    }

    /** Whether this agent is stateless. */
    public boolean isStateless() {
        return stateMutability == StateMutability.STATELESS;
    }

    /** Whether this agent has a given capability. */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }
}
