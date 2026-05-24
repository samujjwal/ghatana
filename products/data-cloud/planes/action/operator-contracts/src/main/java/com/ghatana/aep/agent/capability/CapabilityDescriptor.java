package com.ghatana.aep.agent.capability;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Describes an executable capability exposed by a typed agent
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CapabilityDescriptor(
        CapabilityId id,
        CapabilityKind kind,
        String agentRef,
        Optional<AgentDescriptor> agentDescriptor,
        String inputSchema,
        String outputSchema,
        AgentSideEffectProfile sideEffectProfile,
        List<String> tags,
        Map<String, Object> policies,
        Map<String, String> metadata) {

    public CapabilityDescriptor {
        id = Objects.requireNonNull(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        if (agentRef == null || agentRef.isBlank()) {
            throw new IllegalArgumentException("agentRef must not be blank");
        }
        agentDescriptor = agentDescriptor != null ? agentDescriptor : Optional.empty();
        if (inputSchema == null || inputSchema.isBlank()) {
            throw new IllegalArgumentException("inputSchema must not be blank");
        }
        if (outputSchema == null || outputSchema.isBlank()) {
            throw new IllegalArgumentException("outputSchema must not be blank");
        }
        sideEffectProfile = Objects.requireNonNull(sideEffectProfile, "sideEffectProfile");
        tags = List.copyOf(tags != null ? tags : List.of());
        policies = Map.copyOf(policies != null ? policies : Map.of());
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
    }
}
