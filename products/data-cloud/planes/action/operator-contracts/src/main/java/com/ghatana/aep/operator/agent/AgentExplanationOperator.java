package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.core.operator.OperatorId;

/**
 * Agent operator that explains matches, evidence, confidence, and uncertainty.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_EXPLANATION as a first-class EventOperator
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentExplanationOperator extends AbstractAgentInferenceOperator {

    public AgentExplanationOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_EXPLANATION, agentRef, inputSchema, outputSchema, invocationClient);
    }
}
