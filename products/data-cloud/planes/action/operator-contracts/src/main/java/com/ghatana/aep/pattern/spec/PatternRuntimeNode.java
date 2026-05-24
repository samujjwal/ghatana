package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Represents one deterministic operator node in a compiled PatternSpec runtime graph
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternRuntimeNode(
        String nodeId,
        OperatorKind operatorKind,
        Optional<String> eventType,
        Optional<String> agentRef,
        Optional<String> outputSchema,
        Map<String, Object> parameters,
        List<PatternRuntimeNode> children) {

    public PatternRuntimeNode {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        if (operatorKind == null) {
            throw new IllegalArgumentException("operatorKind must not be null");
        }
        eventType = eventType != null ? eventType : Optional.empty();
        agentRef = agentRef != null ? agentRef : Optional.empty();
        outputSchema = outputSchema != null ? outputSchema : Optional.empty();
        parameters = Map.copyOf(parameters != null ? parameters : Map.of());
        children = List.copyOf(children != null ? children : List.of());
    }

    public boolean isAgentOperator() {
        return operatorKind.name().startsWith("AGENT_");
    }
}
