package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.core.operator.OperatorId;

/**
 * Agent operator that emits typed reflection and learning feedback.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_REFLECTION as an event-operator capability role
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentReflectionOperator extends AbstractAgentInferenceOperator {

    public AgentReflectionOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_REFLECTION, agentRef, inputSchema, outputSchema, invocationClient);
    }
}
