package com.ghatana.core.operator.catalog;

import com.ghatana.aep.agent.capability.EventOperatorCapability;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Searchable metadata entry for an operator catalog registration.
 *
 * @doc.type record
 * @doc.purpose Captures operator metadata needed for governance, discovery, and runtime admission
 * @doc.layer core
 * @doc.pattern CatalogEntry
 */
public record OperatorCatalogEntry(
        OperatorId operatorId,
        OperatorType operatorType,
        Optional<AgentCapabilityRole> agentCapabilityRole,
        String inputSchema,
        String outputSchema,
        Optional<AgentSideEffectProfile> sideEffectProfile,
        String replayProfile,
        String owner,
        String version,
        List<String> capabilities,
        Map<String, String> metadata
) {

    public OperatorCatalogEntry {
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(operatorType, "operatorType");
        agentCapabilityRole = agentCapabilityRole == null ? Optional.empty() : agentCapabilityRole;
        inputSchema = inputSchema == null ? "" : inputSchema;
        outputSchema = outputSchema == null ? "" : outputSchema;
        sideEffectProfile = sideEffectProfile == null ? Optional.empty() : sideEffectProfile;
        replayProfile = replayProfile == null ? "" : replayProfile;
        owner = owner == null ? "" : owner;
        version = version == null ? "" : version;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static OperatorCatalogEntry fromOperator(UnifiedOperator operator) {
        Objects.requireNonNull(operator, "operator");
        if (operator instanceof EventOperatorCapability<?, ?> capability) {
            Map<String, String> metadata = new HashMap<>(operator.getMetadata());
            metadata.put(OperatorCatalogAdmissionPolicy.METADATA_TOOL_POLICY_DECLARED,
                String.valueOf(!capability.descriptor().policies().getOrDefault("toolPolicy", Map.of()).equals(Map.of())));
            return new OperatorCatalogEntry(
                operator.getId(),
                operator.getType(),
                Optional.of(AgentCapabilityRole.valueOf(capability.kind().name())),
                capability.descriptor().inputSchema(),
                capability.descriptor().outputSchema(),
                Optional.of(capability.descriptor().sideEffectProfile()),
                replayProfile(capability),
                operator.getMetadata().getOrDefault("owner", ""),
                operator.getVersion(),
                operator.getCapabilities(),
                metadata);
        }
        return new OperatorCatalogEntry(
            operator.getId(),
            operator.getType(),
            Optional.empty(),
            operator.getMetadata().getOrDefault("inputSchema", ""),
            operator.getMetadata().getOrDefault("outputSchema", ""),
            Optional.empty(),
            operator.getMetadata().getOrDefault("replayProfile", ""),
            operator.getMetadata().getOrDefault("owner", ""),
            operator.getVersion(),
            operator.getCapabilities(),
            operator.getMetadata());
    }

    private static String replayProfile(EventOperatorCapability<?, ?> capability) {
        Object replayPolicy = capability.descriptor().policies().get("replayPolicy");
        if (replayPolicy instanceof Map) {
            @SuppressWarnings("rawtypes")
            Map map = (Map) replayPolicy;
            Object mode = map.getOrDefault("mode", "");
            return mode != null ? String.valueOf(mode) : "";
        }
        return "";
    }
}
