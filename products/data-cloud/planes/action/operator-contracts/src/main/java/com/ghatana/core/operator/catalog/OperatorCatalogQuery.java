package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorType;
import com.ghatana.core.operator.agent.AgentCapabilityRole;
import com.ghatana.core.operator.agent.AgentSideEffectProfile;

import java.util.Optional;

/**
 * Query filters for searching operator catalog metadata.
 *
 * @doc.type record
 * @doc.purpose Provides catalog filters for operator kind, agent kind, side-effect profile, and capability
 * @doc.layer core
 * @doc.pattern Query
 */
public record OperatorCatalogQuery(
        Optional<OperatorType> operatorType,
        Optional<AgentCapabilityRole> agentCapabilityRole,
        Optional<AgentSideEffectProfile> sideEffectProfile,
        Optional<String> capability
) {

    public OperatorCatalogQuery {
        operatorType = operatorType == null ? Optional.empty() : operatorType;
        agentCapabilityRole = agentCapabilityRole == null ? Optional.empty() : agentCapabilityRole;
        sideEffectProfile = sideEffectProfile == null ? Optional.empty() : sideEffectProfile;
        capability = capability == null ? Optional.empty() : capability.filter(value -> !value.isBlank());
    }

    public static OperatorCatalogQuery all() {
        return new OperatorCatalogQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static OperatorCatalogQuery agentKind(AgentCapabilityRole kind) {
        return new OperatorCatalogQuery(Optional.empty(), Optional.of(kind), Optional.empty(), Optional.empty());
    }
}
