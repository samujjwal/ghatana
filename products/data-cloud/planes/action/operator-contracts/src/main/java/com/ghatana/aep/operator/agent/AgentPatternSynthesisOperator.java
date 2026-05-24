package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.core.operator.OperatorId;

import java.util.List;

/**
 * Agent operator that proposes candidate PatternSpec definitions from evidence.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_PATTERN_SYNTHESIS as a governed pattern suggestion operator
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentPatternSynthesisOperator extends AbstractAgentInferenceOperator {

    public AgentPatternSynthesisOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_PATTERN_SYNTHESIS, agentRef, inputSchema, outputSchema, invocationClient);
    }

    @Override
    protected void validatePolicies(OperatorSpec spec, List<String> errors) {
        Object emits = spec.parameters().get("emits");
        if (!"pattern.suggested".equals(emits)) {
            errors.add("AGENT_PATTERN_SYNTHESIS must emit pattern.suggested");
        }
        if (isTrue(spec.policies(), "autoActivate")) {
            errors.add("AGENT_PATTERN_SYNTHESIS must not auto-activate patterns");
        }
    }
}
