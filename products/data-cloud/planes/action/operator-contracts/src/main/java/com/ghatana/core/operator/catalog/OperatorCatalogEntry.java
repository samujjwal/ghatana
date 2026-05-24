package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.UnifiedOperator;
import com.ghatana.core.operator.agent.AgentOperator;
import com.ghatana.core.operator.agent.AgentOperatorKind;
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
        Optional<AgentOperatorKind> agentOperatorKind,
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
        agentOperatorKind = agentOperatorKind == null ? Optional.empty() : agentOperatorKind;
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
        if (operator instanceof AgentOperator agentOperator) {
            Map<String, String> metadata = new HashMap<>(agentOperator.getMetadata());
            metadata.put(OperatorCatalogAdmissionPolicy.METADATA_TOOL_POLICY_DECLARED,
                String.valueOf(!agentOperator.toolPolicy().isEmpty()));
            return new OperatorCatalogEntry(
                agentOperator.getId(),
                agentOperator.getType(),
                Optional.of(agentOperator.agentOperatorKind()),
                agentOperator.inputSchema(),
                agentOperator.outputSchema(),
                Optional.of(agentOperator.sideEffectProfile()),
                String.valueOf(agentOperator.replayPolicy().getOrDefault("mode", "")),
                agentOperator.getMetadata().getOrDefault("owner", ""),
                agentOperator.getVersion(),
                agentOperator.getCapabilities(),
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
}
