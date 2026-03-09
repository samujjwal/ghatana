/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent;

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
 * for the purposes of scheduling, routing, discovery, and capacity
 * planning — without requiring access to the running agent instance.
 *
 * <p>This supersedes the simpler {@link AgentCapabilities} record from
 * the original agent framework. New code should use {@code AgentDescriptor}
 * for richer metadata.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentDescriptor descriptor = AgentDescriptor.builder()
 *     .agentId("fraud-detector-v2")
 *     .name("Fraud Detector")
 *     .version("2.1.0")
 *     .type(AgentType.HYBRID)
 *     .determinism(DeterminismGuarantee.CONFIG_SCOPED)
 *     .latencySla(Duration.ofMillis(50))
 *     .stateMutability(StateMutability.LOCAL_STATE)
 *     .failureMode(FailureMode.CIRCUIT_BREAKER)
 *     .capabilities(Set.of("fraud-detection", "risk-scoring"))
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Agent identity and capability metadata
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@Builder(toBuilder = true)
public class AgentDescriptor {

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    /** Globally unique agent ID (e.g., "fraud-detector-v2"). */
    String agentId;

    /** Human-readable name for display and documentation. */
    String name;

    /** Semantic version string (e.g., "2.1.0"). */
    @Builder.Default
    String version = "1.0.0";

    /** Optional description of what the agent does. */
    String description;

    /** Namespace for multi-tenant or organizational grouping. */
    @Builder.Default
    String namespace = "default";

    // ═══════════════════════════════════════════════════════════════════════════
    // Type Classification
    // ═══════════════════════════════════════════════════════════════════════════

    /** Primary agent type from the unified taxonomy. */
    AgentType type;

    /** Optional subtype for finer classification (e.g., "RULE_BASED", "LLM"). */
    String subtype;

    /** Determinism guarantee this agent provides. */
    @Builder.Default
    DeterminismGuarantee determinism = DeterminismGuarantee.NONE;

    // ═══════════════════════════════════════════════════════════════════════════
    // SLAs and Constraints
    // ═══════════════════════════════════════════════════════════════════════════

    /** Maximum acceptable processing latency for a single input. */
    @Builder.Default
    Duration latencySla = Duration.ofSeconds(5);

    /** Target throughput in events per second. */
    @Builder.Default
    long throughputTarget = 1000;

    // ═══════════════════════════════════════════════════════════════════════════
    // Operational Characteristics
    // ═══════════════════════════════════════════════════════════════════════════

    /** How this agent manages mutable state. */
    @Builder.Default
    StateMutability stateMutability = StateMutability.STATELESS;

    /** How this agent behaves on processing failure. */
    @Builder.Default
    FailureMode failureMode = FailureMode.FAIL_FAST;

    // ═══════════════════════════════════════════════════════════════════════════
    // Capabilities
    // ═══════════════════════════════════════════════════════════════════════════

    /** Set of capability tags for discovery and routing. */
    @Builder.Default
    Set<String> capabilities = Set.of();

    /** Set of event types this agent can consume as input. Empty means any. */
    @Builder.Default
    Set<String> inputEventTypes = Set.of();

    /** Set of event types this agent produces as output. */
    @Builder.Default
    Set<String> outputEventTypes = Set.of();

    /** Arbitrary metadata for extensions. */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Labels & Annotations (Kubernetes-style)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Key-value labels for filtering and selection. */
    @Builder.Default
    Map<String, String> labels = Map.of();

    /** Key-value annotations for non-identifying metadata. */
    @Builder.Default
    Map<String, String> annotations = Map.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Convenience
    // ═══════════════════════════════════════════════════════════════════════════

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

    /**
     * Creates a backward-compatible {@link AgentCapabilities} from this descriptor.
     *
     * @return an AgentCapabilities record
     */
    public AgentCapabilities toCapabilities() {
        return new AgentCapabilities(
                name,
                type != null ? type.name() : "UNKNOWN",
                description,
                capabilities,
                Set.of() // tools — not tracked in descriptor
        );
    }
}
